package com.umdc.smartkin.jpa.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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
public class ChildEntity {
    @Id
    @Column(name = "child_id", nullable = false)
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_id", nullable = false)
    private UserEntity userEntity;

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

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserEntity getUser() {
        return userEntity;
    }

    public void setUser(UserEntity userEntity) {
        this.userEntity = userEntity;
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
