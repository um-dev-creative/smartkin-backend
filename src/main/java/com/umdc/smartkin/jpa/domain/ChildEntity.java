package com.umdc.smartkin.jpa.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * Represents a child user in the SmartKin system.
 * This entity is mapped to the 'children' table in the 'smartkin' schema.
 *
 * Fields:
 * - id: Unique identifier for the child (UUID).
 * - parent: The parent associated with the child.
 *
 * Additional fields and relationships may be present.
 */
@Entity
@Table(name = "child", schema = "smartkin")
public class ChildEntity implements Persistable<UUID> {
    /// Backbone user id of the child; identity lives in backbone, so there is no foreign key to its tables.
    @Id
    @Column(name = "child_id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "parent_id", nullable = false)
    private ParentEntity parent;

    @Size(max = 50)
    @Column(name = "grade_level", length = 50)
    private String gradeLevel;

    @Size(max = 255)
    @Column(name = "avatar")
    private String avatar;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "total_points", nullable = false)
    private Integer totalPoints;

    /// The id is assigned by backbone, not generated, so Spring Data cannot tell a new row from an existing one by looking at it and would
    /// `merge` (select, then insert or UPDATE). For a profile that is a silent takeover: a lost race between two accepted invitations would
    /// overwrite the first parent. A new entity is therefore persisted (a duplicate key fails) and only a loaded or saved one is merged.
    @Transient
    private boolean fresh = true;

    public ChildEntity() {
        // Default constructor
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.fresh = false;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ParentEntity getParent() {
        return parent;
    }

    public void setParent(ParentEntity parent) {
        this.parent = parent;
    }

    public String getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(String gradeLevel) {
        this.gradeLevel = gradeLevel;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Integer getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(Integer totalPoints) {
        this.totalPoints = totalPoints;
    }

}
