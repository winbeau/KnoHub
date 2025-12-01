package com.knohub.backend.repository;

import com.knohub.backend.model.Resource;
import com.knohub.backend.model.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    /**
     * Find all non-deleted resources
     */
    List<Resource> findByDeletedFalse();

    /**
     * Find a resource that is not deleted
     */
    Optional<Resource> findByIdAndDeletedFalse(Long id);

    /**
     * Find all non-deleted resources by type
     */
    List<Resource> findByTypeAndDeletedFalse(ResourceType type);

    /**
     * Search non-deleted resources by title or description
     */
    @Query("SELECT r FROM Resource r WHERE r.deleted = false AND (" +
            "LOWER(r.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(COALESCE(r.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))" +
            ")")
    List<Resource> searchActive(@Param("keyword") String keyword);
}
