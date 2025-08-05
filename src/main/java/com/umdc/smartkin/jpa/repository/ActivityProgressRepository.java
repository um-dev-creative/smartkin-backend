package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.ActivityProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository interface for accessing ActivityProgressEntity data from the database.
 * Extends JpaRepository to provide CRUD operations and query methods for ActivityProgressEntity.
 */
public interface ActivityProgressRepository extends JpaRepository<ActivityProgressEntity, UUID> {
}
