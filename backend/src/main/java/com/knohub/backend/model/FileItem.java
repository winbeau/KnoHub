package com.knohub.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * FileItem entity representing files and folders in the system.
 * Supports soft delete with sequence numbering for same-name re-uploads.
 */
@Entity
@Table(name = "file_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /**
     * Original name before any deletion marking
     */
    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private boolean isFolder;

    /**
     * File type extension (pdf, doc, docx, png, jpg, zip, etc.)
     * Null for folders
     */
    private String type;

    /**
     * Human-readable file size (e.g., "2.4MB")
     * Null for folders
     */
    private String size;

    /**
     * File size in bytes for sorting/filtering
     */
    private Long sizeBytes;

    /**
     * URL/path to access or download the file
     */
    private String url;

    /**
     * Physical storage path on disk
     */
    private String storagePath;

    /**
     * Parent folder (null for root level items)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private FileItem parent;

    /**
     * Child items (for folders)
     */
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FileItem> children = new ArrayList<>();

    /**
     * Resource this file belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    /**
     * Soft delete flag
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    /**
     * Deletion sequence number for same-name handling
     * When a file is deleted, it gets marked with a sequence number
     * to allow new files with the same name to be uploaded
     */
    private Integer deleteSequence;

    /**
     * Timestamp when the item was deleted
     */
    private LocalDateTime deletedAt;

    /**
     * Display order for sorting (lower values appear first)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (originalName == null) {
            originalName = name;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Helper method to add child item
     */
    public void addChild(FileItem child) {
        children.add(child);
        child.setParent(this);
    }

    /**
     * Helper method to remove child item
     */
    public void removeChild(FileItem child) {
        children.remove(child);
        child.setParent(null);
    }
}
