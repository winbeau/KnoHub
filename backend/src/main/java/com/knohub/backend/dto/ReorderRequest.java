package com.knohub.backend.dto;

import lombok.*;

/**
 * Request DTO for reordering files/folders
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReorderRequest {
    private Long dragId;
    private Long dropId;
    private String position; // "before", "after", "inside"
}
