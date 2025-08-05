package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.GoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GoalRepository extends JpaRepository<GoalEntity, UUID> {
}

