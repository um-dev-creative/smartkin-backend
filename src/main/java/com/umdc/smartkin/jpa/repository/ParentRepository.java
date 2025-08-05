package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.ParentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ParentRepository extends JpaRepository<ParentEntity, UUID> {
}

