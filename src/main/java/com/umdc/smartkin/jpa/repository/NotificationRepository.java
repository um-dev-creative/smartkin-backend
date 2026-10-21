package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.NotificationEntity;
import com.umdc.smartkin.jpa.domain.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    /// Whether the user already has a notification of that type about that entity; consumers use it to ignore redelivered events.
    boolean existsByUserIdAndTypeAndRelatedEntityId(UUID userId, NotificationType type, UUID relatedEntityId);

    /// The notifications of a user, in the order of the page request.
    Page<NotificationEntity> findByUserId(UUID userId, Pageable pageable);

    /// The unread notifications of a user, in the order of the page request.
    Page<NotificationEntity> findByUserIdAndIsReadFalse(UUID userId, Pageable pageable);

    /// How many notifications of the user are still unread.
    long countByUserIdAndIsReadFalse(UUID userId);

    /// A notification, only if it belongs to the user.
    Optional<NotificationEntity> findByIdAndUserId(UUID id, UUID userId);

    /// Marks every unread notification of the user as read.
    ///
    /// @param userId the user
    /// @param readAt the instant to record
    /// @return how many notifications changed
    @Transactional
    @Modifying
    @Query("update NotificationEntity n set n.isRead = true, n.readAt = :readAt where n.userId = :userId and n.isRead = false")
    int markAllRead(@Param("userId") UUID userId, @Param("readAt") Instant readAt);
}
