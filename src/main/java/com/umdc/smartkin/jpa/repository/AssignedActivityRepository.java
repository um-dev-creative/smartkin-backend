package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.AssignedActivityEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Repository interface for accessing AssignedActivityEntity data from the database.
 * Extends JpaRepository to provide CRUD operations and query methods for AssignedActivityEntity.
 */
public interface AssignedActivityRepository extends JpaRepository<AssignedActivityEntity, UUID> {

    /**
     * Finds the assignments of a child.
     *
     * @param childId  the child's id
     * @param pageable page and order
     * @return the page of assignments
     */
    @EntityGraph(attributePaths = {"activity", "assignedBy", "child"})
    Page<AssignedActivityEntity> findByChildId(UUID childId, Pageable pageable);

    /**
     * Finds the assignments of every child of a parent.
     *
     * @param parentId the parent's id
     * @param pageable page and order
     * @return the page of assignments
     */
    @EntityGraph(attributePaths = {"activity", "assignedBy", "child"})
    Page<AssignedActivityEntity> findByChildParentId(UUID parentId, Pageable pageable);

    /**
     * Tells whether the child already has the activity assigned and not completed.
     *
     * @param childId    the child's id
     * @param activityId the activity's id
     * @return `true` when an open assignment exists
     */
    @Query("""
            select count(a) > 0 from AssignedActivityEntity a
            where a.child.id = :childId and a.activity.id = :activityId
              and not exists (select p.id from ActivityProgressEntity p where p.assignment = a and p.completionTime is not null)
            """)
    boolean existsOpenAssignment(@Param("childId") UUID childId, @Param("activityId") UUID activityId);
}
