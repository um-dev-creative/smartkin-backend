package com.umdc.smartkin.jpa.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents the progress of an assigned activity in the SmartKin system.
 * This entity is mapped to the 'activity_progress' table in the 'smartkin' schema.
 *
 * Fields:
 * - id: Unique identifier for the progress record (UUID).
 * - assignment: The assigned activity being tracked.
 * - completionPercentage: Completion percentage of the activity.
 *
 * Additional fields and relationships may be present.
 */
@Entity
@Table(name = "activity_progress", schema = "smartkin")
public class ActivityProgressEntity {
    @Id
    @ColumnDefault("smartkin.uuid_generate_v4()")
    @Column(name = "progress_id", nullable = false)
    private UUID id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "assignment_id", nullable = false)
    private AssignedActivityEntity assignment;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "completion_percentage", nullable = false)
    private Integer completionPercentage;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "last_accessed")
    private Instant lastAccessed;

    @Column(name = "completion_time")
    private Instant completionTime;

    @Column(name = "score")
    private Integer score;

    @Column(name = "parent_feedback", length = Integer.MAX_VALUE)
    private String parentFeedback;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public AssignedActivityEntity getAssignment() {
        return assignment;
    }

    public void setAssignment(AssignedActivityEntity assignment) {
        this.assignment = assignment;
    }

    public Integer getCompletionPercentage() {
        return completionPercentage;
    }

    public void setCompletionPercentage(Integer completionPercentage) {
        this.completionPercentage = completionPercentage;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getLastAccessed() {
        return lastAccessed;
    }

    public void setLastAccessed(Instant lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    public Instant getCompletionTime() {
        return completionTime;
    }

    public void setCompletionTime(Instant completionTime) {
        this.completionTime = completionTime;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getParentFeedback() {
        return parentFeedback;
    }

    public void setParentFeedback(String parentFeedback) {
        this.parentFeedback = parentFeedback;
    }

}
