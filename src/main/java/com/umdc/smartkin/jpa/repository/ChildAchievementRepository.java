package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.ChildAchievementEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    /**
     * Lists the ids of the achievements a child has already earned.
     *
     * @param childId the child's id
     * @return the achievement ids
     */
    @Query("select ca.achievement.id from ChildAchievementEntity ca where ca.child.id = :childId")
    List<UUID> findAchievementIdsByChildId(@Param("childId") UUID childId);

    /**
     * Awards an achievement unless the child already has it (the unique index on child and achievement makes the insert a no-op).
     *
     * @param id            id of the award
     * @param childId       the child's id
     * @param achievementId the achievement's id
     * @param earnedAt      when it was earned
     * @return 1 when the achievement was awarded, 0 when the child already had it
     */
    @Transactional
    @Modifying
    @Query(value = """
            insert into smartkin.child_achievements (child_achievement_id, child_id, achievement_id, earned_at)
            values (:id, :childId, :achievementId, :earnedAt)
            on conflict (child_id, achievement_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("childId") UUID childId, @Param("achievementId") UUID achievementId,
                       @Param("earnedAt") Instant earnedAt);
}
