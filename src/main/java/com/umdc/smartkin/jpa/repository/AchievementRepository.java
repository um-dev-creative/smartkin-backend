package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.AchievementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository interface for accessing AchievementEntity data from the database.
 * Extends JpaRepository to provide CRUD operations and query methods for AchievementEntity.
 */
public interface AchievementRepository extends JpaRepository<AchievementEntity, UUID> {
}
