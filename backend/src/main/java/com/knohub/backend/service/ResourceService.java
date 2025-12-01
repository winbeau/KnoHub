package com.knohub.backend.service;

import com.knohub.backend.dto.FileItemDTO;
import com.knohub.backend.dto.ResourceDTO;
import com.knohub.backend.model.Resource;
import com.knohub.backend.model.ResourceType;
import com.knohub.backend.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final FileService fileService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Get all resources
     */
    public List<ResourceDTO> getAllResources() {
        return resourceRepository.findByDeletedFalse().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get resource by ID
     */
    public ResourceDTO getResourceById(Long id) {
        Resource resource = resourceRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("资源不存在或已删除: " + id));
        return toDTO(resource);
    }

    /**
     * Get resources by type
     */
    public List<ResourceDTO> getResourcesByType(String type) {
        ResourceType resourceType = ResourceType.valueOf(type.toUpperCase());
        return resourceRepository.findByTypeAndDeletedFalse(resourceType).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Search resources by keyword
     */
    public List<ResourceDTO> searchResources(String keyword) {
        return resourceRepository.searchActive(keyword).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create a new resource
     */
    @Transactional
    public ResourceDTO createResource(ResourceDTO request) {
        Resource resource = Resource.builder()
                .type(ResourceType.valueOf(request.getType().toUpperCase()))
                .title(request.getTitle())
                .description(request.getDescription())
                .build();

        if (request.getTag() != null) {
            resource.setTag(com.knohub.backend.model.ResourceTag.valueOf(request.getTag().toUpperCase()));
        }

        resource = resourceRepository.save(resource);
        log.info("Resource created: {}", resource.getTitle());
        return toDTO(resource);
    }

    /**
     * Update resource
     */
    @Transactional
    public ResourceDTO updateResource(Long id, ResourceDTO request) {
        Resource resource = resourceRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("资源不存在或已删除: " + id));

        resource.setTitle(request.getTitle());
        resource.setDescription(request.getDescription());
        resource.setType(ResourceType.valueOf(request.getType().toUpperCase()));

        if (request.getTag() != null) {
            resource.setTag(com.knohub.backend.model.ResourceTag.valueOf(request.getTag().toUpperCase()));
        } else {
            resource.setTag(null);
        }

        resource = resourceRepository.save(resource);
        log.info("Resource updated: {}", resource.getTitle());
        return toDTO(resource);
    }

    /**
     * Soft delete resource and mark all attached files/folders
     */
    @Transactional
    public void deleteResource(Long id) {
        Resource resource = resourceRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("资源不存在或已删除: " + id));

        // Soft delete all files/folders under this resource
        fileService.softDeleteResourceFiles(resource);

        resource.setDeleted(true);
        resource.setDeletedAt(LocalDateTime.now());
        resourceRepository.save(resource);
        log.info("Resource soft deleted: {}", id);
    }

    /**
     * Convert Resource entity to DTO
     */
    private ResourceDTO toDTO(Resource resource) {
        List<FileItemDTO> files = fileService.getResourceFiles(resource.getId());

        return ResourceDTO.builder()
                .id(resource.getId())
                .type(resource.getType().getValue())
                .title(resource.getTitle())
                .description(resource.getDescription())
                .tag(resource.getTag() != null ? resource.getTag().getValue() : null)
                .updateDate(resource.getUpdateDate().format(DATE_FORMATTER))
                .files(files)
                .build();
    }
}
