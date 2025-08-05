package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.AssignedActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository interface for accessing AssignedActivityEntity data from the database.
 * Extends JpaRepository to provide CRUD operations and query methods for AssignedActivityEntity.
 */
public interface AssignedActivityRepository extends JpaRepository<AssignedActivityEntity, UUID> {
}
