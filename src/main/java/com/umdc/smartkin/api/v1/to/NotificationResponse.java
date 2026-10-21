package com.umdc.smartkin.api.v1.to;

import com.umdc.smartkin.jpa.domain.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/// An inbox notification.
///
/// @param notificationId   the notification's id
/// @param type             what it is about
/// @param title            short title
/// @param message          the text
/// @param relatedEntityId  the assignment, achievement or other entity it refers to, `null` when none
/// @param createdAt        when it was created
/// @param read             whether the user already read it
/// @param readAt           when it was read, `null` while unread
@Schema(name = "Notification")
public record NotificationResponse(UUID notificationId, NotificationType type, String title, String message, UUID relatedEntityId,
                                   Instant createdAt, boolean read, Instant readAt) {
}
