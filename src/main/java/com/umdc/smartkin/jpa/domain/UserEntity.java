package com.umdc.smartkin.jpa.domain;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * Represents a user in the SmartKin system.
 * This entity is mapped to the 'user' table in the 'general' schema.
 *
 * Fields:
 * - id: Unique identifier for the user (UUID).
 *
 * TODO: Add additional user attributes and relationships as needed.
 */
@Entity
@Table(name = "user", schema = "general")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    public UserEntity() {
        // Default constructor
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    //TODO [Reverse Engineering] generate columns from DB
}
