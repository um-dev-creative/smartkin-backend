package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.ActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository interface for accessing ActivityEntity data from the database.
 * Extends JpaRepository to provide CRUD operations and query methods for ActivityEntity.
 */
public interface ActivityRepository extends JpaRepository<ActivityEntity, UUID> {
}
