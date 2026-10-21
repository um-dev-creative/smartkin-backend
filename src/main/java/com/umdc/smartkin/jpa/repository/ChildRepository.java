package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.ChildEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for accessing ChildEntity data from the database.
 * Extends JpaRepository to provide CRUD operations and query methods for ChildEntity.
 */
public interface ChildRepository extends JpaRepository<ChildEntity, UUID> {

    /**
     * Finds the children linked to a parent.
     *
     * @param parentId the parent's id
     * @return the parent's children
     */
    List<ChildEntity> findAllByParentId(UUID parentId);
}
