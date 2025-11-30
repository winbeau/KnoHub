package com.knohub.backend.service;

import com.knohub.backend.dto.FileItemDTO;
import com.knohub.backend.model.FileItem;
import com.knohub.backend.model.Resource;
import com.knohub.backend.repository.FileItemRepository;
import com.knohub.backend.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.converter.WordToHtmlConverter;
import org.apache.poi.hwpf.usermodel.Picture;
import org.apache.poi.hwpf.usermodel.PictureType;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final FileItemRepository fileItemRepository;
    private final ResourceRepository resourceRepository;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * Upload a file to a resource
     *
     * @param resourceId  Target resource ID
     * @param folderId    Target folder ID (null for root level)
     * @param file        The file to upload
     * @return FileItemDTO of the uploaded file
     */
    @Transactional
    public FileItemDTO uploadFile(Long resourceId, Long folderId, MultipartFile file) throws IOException {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new RuntimeException("资源不存在: " + resourceId));

        FileItem parentFolder = null;
        if (folderId != null) {
            parentFolder = fileItemRepository.findByIdAndDeletedFalse(folderId)
                    .orElseThrow(() -> new RuntimeException("文件夹不存在: " + folderId));
            if (!parentFolder.isFolder()) {
                throw new RuntimeException("目标不是文件夹");
            }
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            originalFilename = "unnamed_file";
        }

        // Check if file with same name exists (non-deleted)
        boolean exists = folderId != null
                ? fileItemRepository.existsByNameInFolder(originalFilename, folderId, resourceId)
                : fileItemRepository.existsByNameAtRoot(originalFilename, resourceId);

        if (exists) {
            throw new RuntimeException("同名文件已存在: " + originalFilename);
        }

        // Extract file extension
        String extension = "";
        int lastDot = originalFilename.lastIndexOf('.');
        if (lastDot > 0) {
            extension = originalFilename.substring(lastDot + 1).toLowerCase();
        }

        // Save file to disk
        String storageName = UUID.randomUUID() + "_" + originalFilename;
        Path uploadPath = Paths.get(uploadDir, String.valueOf(resourceId));
        Files.createDirectories(uploadPath);
        Path filePath = uploadPath.resolve(storageName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Create FileItem entity
        FileItem fileItem = FileItem.builder()
                .name(originalFilename)
                .originalName(originalFilename)
                .isFolder(false)
                .type(extension)
                .size(formatFileSize(file.getSize()))
                .sizeBytes(file.getSize())
                .url("/api/files/" + resourceId + "/download/" + storageName)
                .storagePath(filePath.toString())
                .resource(resource)
                .parent(parentFolder)
                .deleted(false)
                .build();

        fileItem = fileItemRepository.save(fileItem);
        log.info("File uploaded: {} to resource {}, folder {}", originalFilename, resourceId, folderId);

        return toDTO(fileItem);
    }

    /**
     * Soft delete a file (mark as deleted with sequence number)
     *
     * @param fileId File ID to delete
     */
    @Transactional
    public void deleteFile(Long fileId) {
        FileItem file = fileItemRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在: " + fileId));

        if (file.isFolder()) {
            throw new RuntimeException("这是文件夹，请使用文件夹删除接口");
        }

        // Calculate next delete sequence
        Integer maxSequence;
        if (file.getParent() != null) {
            maxSequence = fileItemRepository.findMaxDeleteSequenceByNameInFolder(
                    file.getOriginalName(), file.getParent().getId(), file.getResource().getId());
        } else {
            maxSequence = fileItemRepository.findMaxDeleteSequenceByNameAtRoot(
                    file.getOriginalName(), file.getResource().getId());
        }

        int nextSequence = (maxSequence == null ? 0 : maxSequence) + 1;

        // Mark as deleted
        file.setDeleted(true);
        file.setDeleteSequence(nextSequence);
        file.setDeletedAt(LocalDateTime.now());
        file.setName(file.getOriginalName() + "_deleted_" + nextSequence);

        // Rename physical file
        try {
            Path oldPath = Paths.get(file.getStoragePath());
            if (Files.exists(oldPath)) {
                String newFileName = oldPath.getFileName().toString() + "_deleted_" + nextSequence;
                Path newPath = oldPath.getParent().resolve(newFileName);
                Files.move(oldPath, newPath);
                file.setStoragePath(newPath.toString());
            }
        } catch (IOException e) {
            log.warn("Failed to rename physical file: {}", e.getMessage());
        }

        fileItemRepository.save(file);
        log.info("File soft deleted: {} with sequence {}", file.getOriginalName(), nextSequence);
    }

    /**
     * Create a new folder
     *
     * @param resourceId   Resource ID
     * @param parentFolderId Parent folder ID (null for root)
     * @param folderName   Folder name
     * @return FileItemDTO of the created folder
     */
    @Transactional
    public FileItemDTO createFolder(Long resourceId, Long parentFolderId, String folderName) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new RuntimeException("资源不存在: " + resourceId));

        FileItem parentFolder = null;
        if (parentFolderId != null) {
            parentFolder = fileItemRepository.findByIdAndDeletedFalse(parentFolderId)
                    .orElseThrow(() -> new RuntimeException("父文件夹不存在: " + parentFolderId));
            if (!parentFolder.isFolder()) {
                throw new RuntimeException("目标不是文件夹");
            }
        }

        // Check if folder with same name exists
        boolean exists = parentFolderId != null
                ? fileItemRepository.existsByNameInFolder(folderName, parentFolderId, resourceId)
                : fileItemRepository.existsByNameAtRoot(folderName, resourceId);

        if (exists) {
            throw new RuntimeException("同名文件夹已存在: " + folderName);
        }

        FileItem folder = FileItem.builder()
                .name(folderName)
                .originalName(folderName)
                .isFolder(true)
                .resource(resource)
                .parent(parentFolder)
                .deleted(false)
                .build();

        folder = fileItemRepository.save(folder);
        log.info("Folder created: {} in resource {}", folderName, resourceId);

        return toDTO(folder);
    }

    /**
     * Soft delete a folder and all its contents
     *
     * @param folderId Folder ID to delete
     */
    @Transactional
    public void deleteFolder(Long folderId) {
        FileItem folder = fileItemRepository.findByIdAndDeletedFalse(folderId)
                .orElseThrow(() -> new RuntimeException("文件夹不存在: " + folderId));

        if (!folder.isFolder()) {
            throw new RuntimeException("这是文件，请使用文件删除接口");
        }

        // Recursively delete all children
        deleteFolderRecursive(folder);
    }

    /**
     * Recursively soft delete folder and contents
     */
    private void deleteFolderRecursive(FileItem folder) {
        // First delete all children
        List<FileItem> children = fileItemRepository.findByParentIdAndDeletedFalse(folder.getId());
        for (FileItem child : children) {
            if (child.isFolder()) {
                deleteFolderRecursive(child);
            } else {
                softDeleteItem(child);
            }
        }

        // Then delete the folder itself
        softDeleteItem(folder);
        log.info("Folder soft deleted: {}", folder.getOriginalName());
    }

    /**
     * Common soft delete logic for both files and folders
     */
    private void softDeleteItem(FileItem item) {
        Integer maxSequence;
        if (item.getParent() != null) {
            maxSequence = fileItemRepository.findMaxDeleteSequenceByNameInFolder(
                    item.getOriginalName(), item.getParent().getId(), item.getResource().getId());
        } else {
            maxSequence = fileItemRepository.findMaxDeleteSequenceByNameAtRoot(
                    item.getOriginalName(), item.getResource().getId());
        }

        int nextSequence = (maxSequence == null ? 0 : maxSequence) + 1;

        item.setDeleted(true);
        item.setDeleteSequence(nextSequence);
        item.setDeletedAt(LocalDateTime.now());
        item.setName(item.getOriginalName() + "_deleted_" + nextSequence);

        // Rename physical file if it's a file
        if (!item.isFolder() && item.getStoragePath() != null) {
            try {
                Path oldPath = Paths.get(item.getStoragePath());
                if (Files.exists(oldPath)) {
                    String newFileName = oldPath.getFileName().toString() + "_deleted_" + nextSequence;
                    Path newPath = oldPath.getParent().resolve(newFileName);
                    Files.move(oldPath, newPath);
                    item.setStoragePath(newPath.toString());
                }
            } catch (IOException e) {
                log.warn("Failed to rename physical file: {}", e.getMessage());
            }
        }

        fileItemRepository.save(item);
    }

    /**
     * Reorder files/folders by drag and drop
     *
     * @param dragId   The ID of the item being dragged
     * @param dropId   The ID of the target item
     * @param position The drop position: "before", "after", or "inside" (for folders)
     */
    @Transactional
    public void reorder(Long dragId, Long dropId, String position) {
        FileItem dragItem = fileItemRepository.findByIdAndDeletedFalse(dragId)
                .orElseThrow(() -> new RuntimeException("拖拽项不存在: " + dragId));
        FileItem dropItem = fileItemRepository.findByIdAndDeletedFalse(dropId)
                .orElseThrow(() -> new RuntimeException("目标项不存在: " + dropId));

        if (dragId.equals(dropId)) {
            return;
        }

        // Determine new parent and siblings
        FileItem newParent;
        List<FileItem> siblings;

        if ("inside".equals(position)) {
            // Drop inside a folder
            if (!dropItem.isFolder()) {
                throw new RuntimeException("只能将文件拖入文件夹");
            }
            newParent = dropItem;
            siblings = fileItemRepository.findByParentIdAndDeletedFalseOrderByDisplayOrder(dropItem.getId());
        } else {
            // Drop before or after - same parent as dropItem
            newParent = dropItem.getParent();
            if (newParent != null) {
                siblings = fileItemRepository.findByParentIdAndDeletedFalseOrderByDisplayOrder(newParent.getId());
            } else {
                siblings = fileItemRepository.findByResourceIdAndParentIsNullAndDeletedFalseOrderByDisplayOrder(
                        dropItem.getResource().getId());
            }
        }

        // Remove dragItem from siblings if it's in the same list
        siblings.removeIf(item -> item.getId().equals(dragId));

        // Find insert position
        int insertIndex;
        if ("inside".equals(position)) {
            insertIndex = siblings.size(); // Add at end
        } else {
            int dropIndex = -1;
            for (int i = 0; i < siblings.size(); i++) {
                if (siblings.get(i).getId().equals(dropId)) {
                    dropIndex = i;
                    break;
                }
            }
            if (dropIndex == -1) {
                insertIndex = siblings.size();
            } else {
                insertIndex = "before".equals(position) ? dropIndex : dropIndex + 1;
            }
        }

        // Insert dragItem at the new position
        siblings.add(insertIndex, dragItem);

        // Update parent
        dragItem.setParent(newParent);

        // Update display orders
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).setDisplayOrder(i);
            fileItemRepository.save(siblings.get(i));
        }

        log.info("Reordered item {} to {} relative to {}", dragId, position, dropId);
    }

    /**
     * Get all files/folders for a resource (root level, non-deleted)
     */
    public List<FileItemDTO> getResourceFiles(Long resourceId) {
        List<FileItem> files = fileItemRepository.findByResourceIdAndParentIsNullAndDeletedFalseOrderByDisplayOrder(resourceId);
        return files.stream()
                .map(this::toDTOWithChildren)
                .collect(Collectors.toList());
    }

    /**
     * Get file path for download
     */
    public Path getFilePath(Long resourceId, String filename) {
        return Paths.get(uploadDir, String.valueOf(resourceId), filename);
    }

    /**
     * Render a .doc file to HTML (for preview)
     */
    public String renderDocToHtml(Long fileId) {
        FileItem item = fileItemRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在: " + fileId));

        if (item.isFolder() || item.getType() == null || !"doc".equalsIgnoreCase(item.getType())) {
            throw new RuntimeException("仅支持 .doc 文件预览");
        }

        if (item.getStoragePath() == null || item.getStoragePath().isEmpty()) {
            throw new RuntimeException("未找到物理文件");
        }

        Path path = Paths.get(item.getStoragePath());
        if (!Files.exists(path)) {
            throw new RuntimeException("文件不存在: " + path);
        }

        try (InputStream in = Files.newInputStream(path);
             HWPFDocument document = new HWPFDocument(in)) {

            WordToHtmlConverter converter = new WordToHtmlConverter(
                    DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument());

            converter.setPicturesManager((content, pictureType, suggestedName, widthInches, heightInches) -> {
                String base64 = Base64.getEncoder().encodeToString(content);
                String mime = pictureType != null ? pictureType.getMime() : "image/png";
                return "data:" + mime + ";base64," + base64;
            });

            converter.processDocument(document);

            // Ensure pictures are handled
            List<Picture> pictures = document.getPicturesTable().getAllPictures();
            for (Picture pic : pictures) {
                pic.getContent();
            }

            Document htmlDoc = converter.getDocument();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DOMSource domSource = new DOMSource(htmlDoc);
            StreamResult streamResult = new StreamResult(out);

            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer serializer = factory.newTransformer();
            serializer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            serializer.setOutputProperty(OutputKeys.INDENT, "yes");
            serializer.setOutputProperty(OutputKeys.METHOD, "html");
            serializer.transform(domSource, streamResult);

            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to render .doc to HTML", e);
            throw new RuntimeException("文档预览失败: " + e.getMessage());
        }
    }

    /**
     * Convert FileItem to DTO (without children)
     */
    private FileItemDTO toDTO(FileItem item) {
        return FileItemDTO.builder()
                .id(item.getId())
                .name(item.getName())
                .isFolder(item.isFolder())
                .type(item.getType())
                .size(item.getSize())
                .url(item.getUrl())
                .build();
    }

    /**
     * Convert FileItem to DTO with children (recursive)
     */
    private FileItemDTO toDTOWithChildren(FileItem item) {
        FileItemDTO dto = toDTO(item);
        if (item.isFolder()) {
            List<FileItem> children = fileItemRepository.findByParentIdAndDeletedFalseOrderByDisplayOrder(item.getId());
            dto.setChildren(children.stream()
                    .map(this::toDTOWithChildren)
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    /**
     * Rename a file or folder (does NOT trigger soft delete)
     *
     * @param fileId  File or folder ID to rename
     * @param newName New name for the file or folder
     * @return FileItemDTO of the renamed item
     */
    @Transactional
    public FileItemDTO rename(Long fileId, String newName) {
        FileItem item = fileItemRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new RuntimeException("文件或文件夹不存在: " + fileId));

        if (newName == null || newName.trim().isEmpty()) {
            throw new RuntimeException("名称不能为空");
        }

        String trimmedName = enforceFileExtension(item, newName.trim());

        // Check if a file with the same name already exists in the same location
        boolean exists;
        if (item.getParent() != null) {
            exists = fileItemRepository.existsByNameInFolder(trimmedName, item.getParent().getId(), item.getResource().getId());
        } else {
            exists = fileItemRepository.existsByNameAtRoot(trimmedName, item.getResource().getId());
        }

        // Allow if renaming to the same name (case change, etc.)
        if (exists && !trimmedName.equals(item.getName())) {
            throw new RuntimeException("同名文件或文件夹已存在: " + trimmedName);
        }

        if (!item.isFolder()) {
            renamePhysicalFile(item, trimmedName);
        }

        // Update name (but keep originalName for soft delete tracking)
        item.setName(trimmedName);
        // Also update originalName so future deletes use the new name
        item.setOriginalName(trimmedName);

        fileItemRepository.save(item);
        log.info("Renamed item {} to {}", fileId, trimmedName);

        return toDTO(item);
    }

    /**
     * Ensure file extension stays consistent when renaming files
     */
    private String enforceFileExtension(FileItem item, String candidateName) {
        if (item.isFolder()) {
            return candidateName;
        }

        String oldExt = item.getType();
        String newExt = extractExtension(candidateName);
        String baseName = candidateName;
        int lastDot = candidateName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = candidateName.substring(0, lastDot);
        }

        if (oldExt != null && !oldExt.isEmpty() && !oldExt.equalsIgnoreCase(newExt)) {
            return baseName + "." + oldExt;
        }
        return candidateName;
    }

    /**
     * Rename the physical file on disk and update related metadata.
     */
    private void renamePhysicalFile(FileItem item, String newDisplayName) {
        if (item.getStoragePath() == null) {
            return;
        }

        Path oldPath = Paths.get(item.getStoragePath());
        if (!Files.exists(oldPath)) {
            log.warn("Physical file not found for rename: {}", oldPath);
            return;
        }

        String oldFileName = oldPath.getFileName().toString();
        int underscoreIndex = oldFileName.indexOf('_');
        String prefix = underscoreIndex > 0 ? oldFileName.substring(0, underscoreIndex) : UUID.randomUUID().toString();

        String newStorageName = prefix + "_" + newDisplayName;
        Path newPath = oldPath.getParent().resolve(newStorageName);

        if (oldPath.equals(newPath)) {
            return;
        }

        try {
            Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Failed to rename physical file: {}", e.getMessage());
            throw new RuntimeException("重命名文件失败: 无法修改物理文件");
        }

        item.setStoragePath(newPath.toString());
        item.setUrl("/api/files/" + item.getResource().getId() + "/download/" + newStorageName);

        String newExt = extractExtension(newDisplayName);
        if (newExt != null && !newExt.isEmpty()) {
            item.setType(newExt.toLowerCase());
        }

        try {
            long sizeBytes = Files.size(newPath);
            item.setSizeBytes(sizeBytes);
            item.setSize(formatFileSize(sizeBytes));
        } catch (IOException e) {
            log.warn("Failed to refresh file size after rename: {}", e.getMessage());
        }
    }

    /**
     * Format file size to human-readable string
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024));
        return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Extract extension from filename (without dot)
     */
    private String extractExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1);
        }
        return "";
    }
}
