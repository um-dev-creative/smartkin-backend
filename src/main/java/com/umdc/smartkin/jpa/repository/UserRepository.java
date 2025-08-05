package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository interface for accessing UserEntity data from the database.
 * Extends JpaRepository to provide CRUD operations and query methods for UserEntity.
 */
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
}
