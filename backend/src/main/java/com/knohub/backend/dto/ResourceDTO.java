package com.knohub.backend.dto;

import lombok.*;

import java.util.List;

/**
 * DTO for Resource responses (matches frontend Resource interface)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceDTO {
    private Long id;
    private String type;
    private String title;
    private String description;
    private String tag;
    private String updateDate;
    private List<FileItemDTO> files;
}
