package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.NotificationEntity;
import com.umdc.smartkin.jpa.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    /// Whether the user already has a notification of that type about that entity; consumers use it to ignore redelivered events.
    boolean existsByUserIdAndTypeAndRelatedEntityId(UUID userId, NotificationType type, UUID relatedEntityId);
}
