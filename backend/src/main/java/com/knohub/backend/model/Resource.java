package com.knohub.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Resource entity representing a course material collection
 */
@Entity
@Table(name = "resources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceType type;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    private ResourceTag tag;

    @Column(nullable = false)
    private LocalDate updateDate;

    /**
     * Root level files and folders belonging to this resource
     */
    @OneToMany(mappedBy = "resource", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FileItem> files = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (updateDate == null) {
            updateDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        updateDate = LocalDate.now();
    }

    /**
     * Helper method to add a file to this resource
     */
    public void addFile(FileItem file) {
        files.add(file);
        file.setResource(this);
    }

    /**
     * Helper method to remove a file from this resource
     */
    public void removeFile(FileItem file) {
        files.remove(file);
        file.setResource(null);
    }
}
