package com.umdc.smartkin.jpa.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an activity in the SmartKin system.
 * This entity is mapped to the 'activities' table in the 'smartkin' schema.
 *
 * Fields:
 * - id: Unique identifier for the activity (UUID).
 * - topic: The topic to which this activity belongs.
 * - title: Title of the activity.
 *
 * Additional fields and relationships may be present.
 */
@Entity
@Table(name = "activities", schema = "smartkin")
public class ActivityEntity {
    @Id
    @ColumnDefault("smartkin.uuid_generate_v4()")
    @Column(name = "activity_id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "topic_id", nullable = false)
    private TopicEntity topic;

    @Size(max = 255)
    @NotNull
    @Column(name = "title", nullable = false)
    private String title;

    @NotNull
    @Column(name = "description", nullable = false, length = Integer.MAX_VALUE)
    private String description;

    @NotNull
    @Column(name = "content", nullable = false, length = Integer.MAX_VALUE)
    private String content;

    @NotNull
    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @NotNull
    @Column(name = "points_reward", nullable = false)
    private Integer pointsReward;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @NotNull
    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public TopicEntity getTopic() {
        return topic;
    }

    public void setTopic(TopicEntity topic) {
        this.topic = topic;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Integer getPointsReward() {
        return pointsReward;
    }

    public void setPointsReward(Integer pointsReward) {
        this.pointsReward = pointsReward;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

/*
 TODO [Reverse Engineering] create field to map the 'difficulty_level' column
 Available actions: Define target Java type | Uncomment as is | Remove column mapping
    @Column(name = "difficulty_level", columnDefinition = "difficulty_level not null")
    private Object difficultyLevel;
*/
}
