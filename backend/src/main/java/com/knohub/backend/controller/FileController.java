package com.knohub.backend.controller;

import com.knohub.backend.dto.ApiResponse;
import com.knohub.backend.dto.CreateFolderRequest;
import com.knohub.backend.dto.FileItemDTO;
import com.knohub.backend.dto.RenameRequest;
import com.knohub.backend.dto.ReorderRequest;
import com.knohub.backend.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class FileController {

    private final FileService fileService;

    /**
     * Upload a file to a resource
     */
    @PostMapping("/{resourceId}/upload")
    public ResponseEntity<ApiResponse<FileItemDTO>> uploadFile(
            @PathVariable Long resourceId,
            @RequestParam(required = false) Long folderId,
            @RequestParam("file") MultipartFile file) {

        try {
            FileItemDTO result = fileService.uploadFile(resourceId, folderId, file);
            return ResponseEntity.ok(ApiResponse.success("文件上传成功", result));
        } catch (IOException e) {
            log.error("File upload failed", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("文件上传失败: " + e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Batch upload files to a resource
     */
    @PostMapping("/{resourceId}/upload/batch")
    public ResponseEntity<ApiResponse<List<FileItemDTO>>> uploadFiles(
            @PathVariable Long resourceId,
            @RequestParam(required = false) Long folderId,
            @RequestParam("files") MultipartFile[] files) {

        try {
            List<FileItemDTO> result = fileService.uploadFiles(resourceId, folderId, files);
            return ResponseEntity.ok(ApiResponse.success("批量上传成功", result));
        } catch (IOException e) {
            log.error("Batch file upload failed", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("文件上传失败: " + e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Delete a file (soft delete)
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable Long fileId) {
        try {
            fileService.deleteFile(fileId);
            return ResponseEntity.ok(ApiResponse.success("文件删除成功", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Create a folder
     */
    @PostMapping("/{resourceId}/folders")
    public ResponseEntity<ApiResponse<FileItemDTO>> createFolder(
            @PathVariable Long resourceId,
            @RequestBody CreateFolderRequest request) {

        try {
            FileItemDTO result = fileService.createFolder(resourceId, request.getParentFolderId(), request.getName());
            return ResponseEntity.ok(ApiResponse.success("文件夹创建成功", result));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Delete a folder (soft delete)
     */
    @DeleteMapping("/folders/{folderId}")
    public ResponseEntity<ApiResponse<Void>> deleteFolder(@PathVariable Long folderId) {
        try {
            fileService.deleteFolder(folderId);
            return ResponseEntity.ok(ApiResponse.success("文件夹删除成功", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Reorder files/folders
     */
    @PostMapping("/reorder")
    public ResponseEntity<ApiResponse<Void>> reorder(@RequestBody ReorderRequest request) {
        try {
            fileService.reorder(request.getDragId(), request.getDropId(), request.getPosition());
            return ResponseEntity.ok(ApiResponse.success("排序成功", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Rename a file or folder
     */
    @PutMapping("/{fileId}/rename")
    public ResponseEntity<ApiResponse<FileItemDTO>> rename(
            @PathVariable Long fileId,
            @RequestBody RenameRequest request) {
        try {
            FileItemDTO result = fileService.rename(fileId, request.getNewName());
            return ResponseEntity.ok(ApiResponse.success("重命名成功", result));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get all files/folders for a resource
     */
    @GetMapping("/{resourceId}")
    public ResponseEntity<ApiResponse<List<FileItemDTO>>> getResourceFiles(@PathVariable Long resourceId) {
        try {
            List<FileItemDTO> files = fileService.getResourceFiles(resourceId);
            return ResponseEntity.ok(ApiResponse.success(files));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Preview .doc file as HTML
     */
    @GetMapping("/{fileId}/html")
    public ResponseEntity<ApiResponse<String>> previewDocAsHtml(@PathVariable Long fileId) {
        try {
            String html = fileService.renderDocToHtml(fileId);
            return ResponseEntity.ok(ApiResponse.success(html));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Stream a generated preview (currently supports .circ rendered via Logisim).
     */
    @GetMapping("/{fileId}/preview")
    public ResponseEntity<Resource> getPreview(@PathVariable Long fileId) {
        try {
            Path previewPath = fileService.getPreviewPath(fileId);
            Resource resource = new UrlResource(previewPath.toUri());
            String contentType = Files.probeContentType(previewPath);
            if (contentType == null) {
                contentType = "image/png";
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + previewPath.getFileName() + "\"")
                    .body(resource);
        } catch (IOException e) {
            log.error("Preview streaming failed", e);
            return ResponseEntity.internalServerError().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Download a file
     */
    @GetMapping("/{resourceId}/download/{filename}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long resourceId,
            @PathVariable String filename) {

        try {
            Path filePath = fileService.getFilePath(resourceId, filename);

            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(filePath.toUri());
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            // Extract original filename (remove UUID prefix)
            String originalFilename = filename;
            int underscoreIndex = filename.indexOf('_');
            if (underscoreIndex > 0 && underscoreIndex < filename.length() - 1) {
                originalFilename = filename.substring(underscoreIndex + 1);
            }

            String encodedFilename = URLEncoder.encode(originalFilename, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFilename)
                    .body(resource);
        } catch (IOException e) {
            log.error("File download failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
