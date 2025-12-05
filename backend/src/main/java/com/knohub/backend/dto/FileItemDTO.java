package com.knohub.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * DTO for FileItem responses (matches frontend FileItem interface)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileItemDTO {
    private Long id;
    private String name;

    @JsonProperty("isFolder")
    private boolean isFolder;

    private String type;
    private String size;
    private String url;
    private String previewUrl;
    private List<FileItemDTO> children;
}
