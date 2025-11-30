package com.knohub.backend.repository;

import com.knohub.backend.model.Resource;
import com.knohub.backend.model.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    /**
     * Find all resources by type
     */
    List<Resource> findByType(ResourceType type);

    /**
     * Search resources by title or description
     */
    List<Resource> findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String title, String description);

    /**
     * Find resources by type with search
     */
    List<Resource> findByTypeAndTitleContainingIgnoreCaseOrTypeAndDescriptionContainingIgnoreCase(
            ResourceType type1, String title, ResourceType type2, String description);
}
