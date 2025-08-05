package com.umdc.smartkin.jpa.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an achievement earned by a child in the SmartKin system.
 * This entity is mapped to the 'child_achievements' table in the 'smartkin' schema.
 *
 * Fields:
 * - id: Unique identifier for the child achievement (UUID).
 * - child: The child who earned the achievement.
 * - achievement: The achievement earned.
 *
 * Additional fields and relationships may be present.
 */
@Entity
@Table(name = "child_achievements", schema = "smartkin")
public class ChildAchievementEntity {
    @Id
    @ColumnDefault("smartkin.uuid_generate_v4()")
    @Column(name = "child_achievement_id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "child_id", nullable = false)
    private ChildEntity child;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "achievement_id", nullable = false)
    private AchievementEntity achievement;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;

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

    public AchievementEntity getAchievement() {
        return achievement;
    }

    public void setAchievement(AchievementEntity achievement) {
        this.achievement = achievement;
    }

    public Instant getEarnedAt() {
        return earnedAt;
    }

    public void setEarnedAt(Instant earnedAt) {
        this.earnedAt = earnedAt;
    }

}
