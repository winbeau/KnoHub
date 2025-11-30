package com.knohub.backend.dto;

import lombok.*;

/**
 * Request DTO for creating a folder
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFolderRequest {
    private String name;
    private Long parentFolderId;
}
