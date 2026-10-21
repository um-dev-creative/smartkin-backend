package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.constant.SmartKinAppConstants;
import com.umdc.smartkin.jpa.domain.NotificationEntity;
import com.umdc.smartkin.jpa.domain.NotificationType;
import com.umdc.smartkin.jpa.repository.NotificationRepository;
import com.umdc.smartkin.mapper.NotificationMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.umdc.smartkin.support.TestData.CHILD_ID;
import static com.umdc.smartkin.support.TestData.tokenFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final UUID NOTIFICATION_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Mock
    private NotificationRepository repository;
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(repository, new NotificationMapperImpl(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static NotificationEntity notification(boolean read) {
        var notification = new NotificationEntity();
        notification.setId(NOTIFICATION_ID);
        notification.setUserId(CHILD_ID);
        notification.setType(NotificationType.ASSIGNMENT);
        notification.setTitle("Nueva actividad");
        notification.setMessage("Tienes una nueva actividad");
        notification.setRelatedEntityId(UUID.randomUUID());
        notification.setCreatedAt(NOW.minusSeconds(60));
        notification.setIsRead(read);
        return notification;
    }

    @Test
    void listsTheInboxNewestFirst() {
        when(repository.findByUserId(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification(false)), PageRequest.of(1, 5), 11));

        var response = service.list(tokenFor(CHILD_ID), false, 1, 5);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var page = response.getBody();
        assertEquals(1, page.page());
        assertEquals(5, page.size());
        assertEquals(11, page.totalElements());
        assertEquals(3, page.totalPages());
        var first = page.content().get(0);
        assertEquals(NOTIFICATION_ID, first.notificationId());
        assertEquals(NotificationType.ASSIGNMENT, first.type());
        assertEquals("Nueva actividad", first.title());
        assertEquals(false, first.read());
        assertNull(first.readAt());
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserId(any(UUID.class), pageable.capture());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), pageable.getValue().getSort());
        verify(repository, never()).findByUserIdAndIsReadFalse(any(), any());
    }

    @Test
    void listsOnlyTheUnreadOnesWhenAsked() {
        when(repository.findByUserIdAndIsReadFalse(any(UUID.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(notification(false))));

        var response = service.list(tokenFor(CHILD_ID), true, 0, 20);

        assertEquals(1, response.getBody().content().size());
        verify(repository, never()).findByUserId(any(), any());
    }

    @Test
    void aReadNotificationShowsWhenItWasRead() {
        var read = notification(true);
        read.setReadAt(NOW);
        when(repository.findByUserId(any(UUID.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(read)));

        var first = service.list(tokenFor(CHILD_ID), false, 0, 20).getBody().content().get(0);

        assertEquals(true, first.read());
        assertEquals(NOW, first.readAt());
    }

    @Test
    void listRejectsABadPageOrSizeAndAnUnknownCaller() {
        for (var bad : List.of(new int[]{-1, 20}, new int[]{0, 0}, new int[]{0, 101})) {
            var response = service.list(tokenFor(CHILD_ID), false, bad[0], bad[1]);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals(SmartKinAppConstants.INVALID_PAGE_MESSAGE, response.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
        }
        assertEquals(HttpStatus.FORBIDDEN, service.list("garbage", false, 0, 20).getStatusCode());
        verify(repository, never()).findByUserId(any(), any());
    }

    @Test
    void countsTheUnreadNotifications() {
        when(repository.countByUserIdAndIsReadFalse(CHILD_ID)).thenReturn(4L);

        var response = service.unreadCount(tokenFor(CHILD_ID));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(4, response.getBody().count());
        assertEquals(HttpStatus.FORBIDDEN, service.unreadCount(null).getStatusCode());
    }

    @Test
    void markingOneAsReadRecordsWhenAndIsIdempotent() {
        var unread = notification(false);
        when(repository.findByIdAndUserId(NOTIFICATION_ID, CHILD_ID)).thenReturn(Optional.of(unread));

        var first = service.markRead(tokenFor(CHILD_ID), NOTIFICATION_ID);
        assertEquals(HttpStatus.NO_CONTENT, first.getStatusCode());
        assertTrue(unread.getIsRead());
        assertEquals(NOW, unread.getReadAt());
        verify(repository).save(unread);

        unread.setReadAt(NOW.minusSeconds(500));
        var second = service.markRead(tokenFor(CHILD_ID), NOTIFICATION_ID);
        assertEquals(HttpStatus.NO_CONTENT, second.getStatusCode());
        assertEquals(NOW.minusSeconds(500), unread.getReadAt());
        verify(repository).save(unread);
    }

    @Test
    void anotherUsersNotificationIsNotFound() {
        var other = UUID.randomUUID();
        when(repository.findByIdAndUserId(NOTIFICATION_ID, other)).thenReturn(Optional.empty());

        var response = service.markRead(tokenFor(other), NOTIFICATION_ID);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(SmartKinAppConstants.NOTIFICATION_NOT_FOUND_MESSAGE, response.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
        assertEquals(HttpStatus.FORBIDDEN, service.markRead("garbage", NOTIFICATION_ID).getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    void markingAllAsReadOnlyTouchesTheCallersNotifications() {
        when(repository.markAllRead(CHILD_ID, NOW)).thenReturn(3);

        var response = service.markAllRead(tokenFor(CHILD_ID));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(repository).markAllRead(CHILD_ID, NOW);
        assertEquals(HttpStatus.FORBIDDEN, service.markAllRead(null).getStatusCode());
    }
}
