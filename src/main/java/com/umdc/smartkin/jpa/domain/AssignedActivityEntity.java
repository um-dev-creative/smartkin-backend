package com.umdc.smartkin.jpa.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an activity assigned to a child in the SmartKin system.
 * This entity is mapped to the 'assigned_activities' table in the 'smartkin' schema.
 *
 * Fields:
 * - id: Unique identifier for the assignment (UUID).
 * - child: The child to whom the activity is assigned.
 * - activity: The activity being assigned.
 *
 * Additional fields and relationships may be present.
 */
@Entity
@Table(name = "assigned_activities", schema = "smartkin")
public class AssignedActivityEntity {
    @Id
    @ColumnDefault("smartkin.uuid_generate_v4()")
    @Column(name = "assignment_id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "child_id", nullable = false)
    private ChildEntity child;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "activity_id", nullable = false)
    private ActivityEntity activity;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "assigned_by", nullable = false)
    private ParentEntity assignedBy;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "due_date")
    private Instant dueDate;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ChildEntity getChild() {
        return child;
    }

    public void setChild(ChildEntity child) {
        this.child = child;
    }

    public ActivityEntity getActivity() {
        return activity;
    }

    public void setActivity(ActivityEntity activity) {
        this.activity = activity;
    }

    public ParentEntity getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(ParentEntity assignedBy) {
        this.assignedBy = assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
    }

/*
 TODO [Reverse Engineering] create field to map the 'status' column
 Available actions: Define target Java type | Uncomment as is | Remove column mapping
    @ColumnDefault("'assigned'")
    @Column(name = "status", columnDefinition = "activity_status not null")
    private Object status;
*/
}
