package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.ChildAchievementEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for accessing ChildAchievementEntity data from the database.
 * Extends JpaRepository to provide CRUD operations and query methods for ChildAchievementEntity.
 */
public interface ChildAchievementRepository extends JpaRepository<ChildAchievementEntity, UUID> {

    /**
     * Finds the achievements a child has earned, newest first, loading each achievement in the same query.
     *
     * @param childId the child's id
     * @return the earned achievements
     */
    @EntityGraph(attributePaths = "achievement")
    List<ChildAchievementEntity> findByChildIdOrderByEarnedAtDesc(UUID childId);
}
