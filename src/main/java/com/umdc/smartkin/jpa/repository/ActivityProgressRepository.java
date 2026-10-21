package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.ActivityProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for accessing ActivityProgressEntity data from the database.
 * Extends JpaRepository to provide CRUD operations and query methods for ActivityProgressEntity.
 */
public interface ActivityProgressRepository extends JpaRepository<ActivityProgressEntity, UUID> {

    /**
     * Finds the progress of an assignment.
     *
     * @param assignmentId the assignment's id
     * @return the progress, when the child has started
     */
    Optional<ActivityProgressEntity> findByAssignmentId(UUID assignmentId);

    /**
     * Finds the progress of several assignments at once.
     *
     * @param assignmentIds the assignments' ids
     * @return the progress rows that exist
     */
    List<ActivityProgressEntity> findAllByAssignmentIdIn(Collection<UUID> assignmentIds);

    /**
     * Counts the distinct activities a child has completed.
     *
     * @param childId the child's id
     * @return the number of distinct completed activities
     */
    @Query("""
            select count(distinct p.assignment.activity.id) from ActivityProgressEntity p
            where p.assignment.child.id = :childId and p.completionTime is not null
            """)
    long countCompletedActivities(@Param("childId") UUID childId);
}
