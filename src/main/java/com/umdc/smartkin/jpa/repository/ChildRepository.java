package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.ChildEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository interface for accessing ChildEntity data from the database.
 * Extends JpaRepository to provide CRUD operations and query methods for ChildEntity.
 */
public interface ChildRepository extends JpaRepository<ChildEntity, UUID> {
}
