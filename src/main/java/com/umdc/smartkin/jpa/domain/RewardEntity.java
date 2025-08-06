package com.umdc.smartkin.jpa.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;

import java.util.UUID;

@Entity
@Table(name = "rewards", schema = "smartkin")
public class RewardEntity {
    @Id
    @ColumnDefault("smartkin.uuid_generate_v4()")
    @Column(name = "reward_id", nullable = false)
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
    @Column(name = "points_cost", nullable = false)
    private Integer pointsCost;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "is_digital", nullable = false)
    private Boolean isDigital = false;

    public RewardEntity() {
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

    public Integer getPointsCost() {
        return pointsCost;
    }

    public void setPointsCost(Integer pointsCost) {
        this.pointsCost = pointsCost;
    }

    public Boolean getIsDigital() {
        return isDigital;
    }

    public void setIsDigital(Boolean isDigital) {
        this.isDigital = isDigital;
    }

}
