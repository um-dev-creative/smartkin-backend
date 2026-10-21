package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.ActivityProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
