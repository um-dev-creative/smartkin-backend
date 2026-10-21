package com.umdc.smartkin.mapper;

import com.umdc.smartkin.jpa.domain.NotificationEntity;
import com.umdc.smartkin.jpa.domain.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotificationMapperTest {

    private final NotificationMapper mapper = new NotificationMapperImpl();

    @Test
    void mapsAnUnreadAndAReadNotification() {
        var id = UUID.randomUUID();
        var related = UUID.randomUUID();
        var notification = new NotificationEntity();
        notification.setId(id);
        notification.setType(NotificationType.REMINDER);
        notification.setTitle("Recordatorio");
        notification.setMessage("Termina tu actividad");
        notification.setRelatedEntityId(related);
        notification.setCreatedAt(Instant.parse("2026-10-21T10:00:00Z"));
        notification.setIsRead(false);

        var unread = mapper.toResponse(notification);
        notification.setIsRead(true);
        notification.setReadAt(Instant.parse("2026-10-21T11:00:00Z"));
        var read = mapper.toResponse(notification);

        assertEquals(id, unread.notificationId());
        assertEquals(NotificationType.REMINDER, unread.type());
        assertEquals("Recordatorio", unread.title());
        assertEquals("Termina tu actividad", unread.message());
        assertEquals(related, unread.relatedEntityId());
        assertEquals(Instant.parse("2026-10-21T10:00:00Z"), unread.createdAt());
        assertEquals(false, unread.read());
        assertNull(unread.readAt());
        assertEquals(true, read.read());
        assertEquals(Instant.parse("2026-10-21T11:00:00Z"), read.readAt());
    }
}
