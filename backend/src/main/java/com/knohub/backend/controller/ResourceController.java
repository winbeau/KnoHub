package com.knohub.backend.controller;

import com.knohub.backend.dto.ApiResponse;
import com.knohub.backend.dto.ResourceDTO;
import com.knohub.backend.service.ResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ResourceController {

    private final ResourceService resourceService;

    /**
     * Get all resources
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ResourceDTO>>> getAllResources() {
        List<ResourceDTO> resources = resourceService.getAllResources();
        return ResponseEntity.ok(ApiResponse.success(resources));
    }

    /**
     * Get resource by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ResourceDTO>> getResourceById(@PathVariable Long id) {
        try {
            ResourceDTO resource = resourceService.getResourceById(id);
            return ResponseEntity.ok(ApiResponse.success(resource));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get resources by type
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<ApiResponse<List<ResourceDTO>>> getResourcesByType(@PathVariable String type) {
        try {
            List<ResourceDTO> resources = resourceService.getResourcesByType(type);
            return ResponseEntity.ok(ApiResponse.success(resources));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("无效的资源类型: " + type));
        }
    }

    /**
     * Search resources
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ResourceDTO>>> searchResources(@RequestParam String keyword) {
        List<ResourceDTO> resources = resourceService.searchResources(keyword);
        return ResponseEntity.ok(ApiResponse.success(resources));
    }

    /**
     * Create a new resource
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ResourceDTO>> createResource(@RequestBody ResourceDTO request) {
        try {
            ResourceDTO resource = resourceService.createResource(request);
            return ResponseEntity.ok(ApiResponse.success("资源创建成功", resource));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Update a resource
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ResourceDTO>> updateResource(
            @PathVariable Long id,
            @RequestBody ResourceDTO request) {

        try {
            ResourceDTO resource = resourceService.updateResource(id, request);
            return ResponseEntity.ok(ApiResponse.success("资源更新成功", resource));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Delete a resource
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteResource(@PathVariable Long id) {
        try {
            resourceService.deleteResource(id);
            return ResponseEntity.ok(ApiResponse.success("资源删除成功", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
