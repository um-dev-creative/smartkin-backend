package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
}

