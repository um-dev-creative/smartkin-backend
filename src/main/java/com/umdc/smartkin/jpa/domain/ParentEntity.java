package com.umdc.smartkin.jpa.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Entity
@Table(name = "parent", schema = "smartkin")
public class ParentEntity {
    /// Backbone user id of the parent; identity lives in backbone, so there is no foreign key to its tables.
    @Id
    @Column(name = "parent_id", nullable = false)
    private UUID id;

    @Size(max = 20)
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "address", length = Integer.MAX_VALUE)
    private String address;

    @Size(max = 255)
    @Column(name = "profile_picture")
    private String profilePicture;

    public ParentEntity() {
        // Default constructor
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture;
    }

}
