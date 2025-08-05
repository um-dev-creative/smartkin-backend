package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.Point;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PointRepository extends JpaRepository<Point, UUID> {
}

