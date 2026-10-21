package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.ActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for accessing ActivityEntity data from the database.
 * Extends JpaRepository to provide CRUD operations and query methods for ActivityEntity.
 */
public interface ActivityRepository extends JpaRepository<ActivityEntity, UUID> {

    /**
     * Finds the active activities, ordered by title.
     *
     * @return the active activities
     */
    List<ActivityEntity> findAllByIsActiveTrueOrderByTitle();

    /**
     * Finds the active activities of a topic, ordered by title.
     *
     * @param topicId the topic's id
     * @return the active activities of the topic
     */
    List<ActivityEntity> findAllByTopicIdAndIsActiveTrueOrderByTitle(UUID topicId);

    /**
     * Finds an active activity.
     *
     * @param id the activity's id
     * @return the activity, when it exists and is active
     */
    Optional<ActivityEntity> findByIdAndIsActiveTrue(UUID id);
}
