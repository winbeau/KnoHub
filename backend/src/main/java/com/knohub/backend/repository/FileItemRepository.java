package com.knohub.backend.repository;

import com.knohub.backend.model.FileItem;
import com.knohub.backend.model.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileItemRepository extends JpaRepository<FileItem, Long> {

    /**
     * Find all non-deleted root-level files for a resource
     */
    List<FileItem> findByResourceIdAndParentIsNullAndDeletedFalse(Long resourceId);

    /**
     * Find all non-deleted root-level files for a resource, ordered by displayOrder
     */
    List<FileItem> findByResourceIdAndParentIsNullAndDeletedFalseOrderByDisplayOrder(Long resourceId);

    /**
     * Find all non-deleted children of a folder
     */
    List<FileItem> findByParentIdAndDeletedFalse(Long parentId);

    /**
     * Find all non-deleted children of a folder, ordered by displayOrder
     */
    List<FileItem> findByParentIdAndDeletedFalseOrderByDisplayOrder(Long parentId);

    /**
     * Find by original name in a specific folder (including deleted for sequence calculation)
     */
    List<FileItem> findByOriginalNameAndParentIdAndResourceId(String originalName, Long parentId, Long resourceId);

    /**
     * Find by original name at root level
     */
    List<FileItem> findByOriginalNameAndParentIsNullAndResourceId(String originalName, Long resourceId);

    /**
     * Get max delete sequence for a given original name in a folder
     */
    @Query("SELECT MAX(f.deleteSequence) FROM FileItem f WHERE f.originalName = :originalName " +
           "AND f.parent.id = :parentId AND f.resource.id = :resourceId AND f.deleted = true")
    Integer findMaxDeleteSequenceByNameInFolder(@Param("originalName") String originalName,
                                                 @Param("parentId") Long parentId,
                                                 @Param("resourceId") Long resourceId);

    /**
     * Get max delete sequence for a given original name at root level
     */
    @Query("SELECT MAX(f.deleteSequence) FROM FileItem f WHERE f.originalName = :originalName " +
           "AND f.parent IS NULL AND f.resource.id = :resourceId AND f.deleted = true")
    Integer findMaxDeleteSequenceByNameAtRoot(@Param("originalName") String originalName,
                                               @Param("resourceId") Long resourceId);

    /**
     * Check if a non-deleted file/folder with the same name exists
     */
    @Query("SELECT COUNT(f) > 0 FROM FileItem f WHERE f.originalName = :name " +
           "AND f.parent.id = :parentId AND f.resource.id = :resourceId AND f.deleted = false")
    boolean existsByNameInFolder(@Param("name") String name,
                                  @Param("parentId") Long parentId,
                                  @Param("resourceId") Long resourceId);

    /**
     * Check if a non-deleted file/folder with the same name exists at root level
     */
    @Query("SELECT COUNT(f) > 0 FROM FileItem f WHERE f.originalName = :name " +
           "AND f.parent IS NULL AND f.resource.id = :resourceId AND f.deleted = false")
    boolean existsByNameAtRoot(@Param("name") String name,
                                @Param("resourceId") Long resourceId);

    /**
     * Find a non-deleted file by ID
     */
    Optional<FileItem> findByIdAndDeletedFalse(Long id);
}
