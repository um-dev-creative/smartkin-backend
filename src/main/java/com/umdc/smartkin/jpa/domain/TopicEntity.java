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
@Table(name = "topics", schema = "smartkin")
public class TopicEntity {
    @Id
    @ColumnDefault("smartkin.uuid_generate_v4()")
    @Column(name = "topic_id", nullable = false)
    private UUID id;

    @Size(max = 100)
    @NotNull
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    @Size(max = 255)
    @Column(name = "icon")
    private String icon;

    @Column(name = "age_range_min")
    private Integer ageRangeMin;

    @Column(name = "age_range_max")
    private Integer ageRangeMax;

    public TopicEntity() {
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

    public Integer getAgeRangeMin() {
        return ageRangeMin;
    }

    public void setAgeRangeMin(Integer ageRangeMin) {
        this.ageRangeMin = ageRangeMin;
    }

    public Integer getAgeRangeMax() {
        return ageRangeMax;
    }

    public void setAgeRangeMax(Integer ageRangeMax) {
        this.ageRangeMax = ageRangeMax;
    }

}
