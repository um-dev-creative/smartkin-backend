package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.PointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PointRepository extends JpaRepository<PointEntity, UUID> {
}

