package com.umdc.smartkin.jpa.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;

import java.util.UUID;

/**
 * Represents an achievement in the SmartKin system.
 * This entity is mapped to the 'achievements' table in the 'smartkin' schema.
 *
 * Fields:
 * - id: Unique identifier for the achievement (UUID).
 * - name: Name of the achievement.
 * - description: Description of the achievement.
 *
 * Additional fields and relationships may be present.
 */
@Entity
@Table(name = "achievements", schema = "smartkin")
public class AchievementEntity {
    @Id
    @ColumnDefault("smartkin.uuid_generate_v4()")
    @Column(name = "achievement_id", nullable = false)
    private UUID id;

    @Size(max = 100)
    @NotNull
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @NotNull
    @Column(name = "description", nullable = false, length = Integer.MAX_VALUE)
    private String description;

    @Size(max = 255)
    @Column(name = "icon")
    private String icon;

    @NotNull
    @Column(name = "points_value", nullable = false)
    private Integer pointsValue;

    @NotNull
    @Column(name = "requirements", nullable = false, length = Integer.MAX_VALUE)
    private String requirements;

    public AchievementEntity() {
        // Default constructor
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getPointsValue() {
        return pointsValue;
    }

    public void setPointsValue(Integer pointsValue) {
        this.pointsValue = pointsValue;
    }

    public String getRequirements() {
        return requirements;
    }

    public void setRequirements(String requirements) {
        this.requirements = requirements;
    }

}
