package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.RewardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RewardRepository extends JpaRepository<RewardEntity, UUID> {
}

