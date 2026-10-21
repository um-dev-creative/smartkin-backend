package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.NotificationService;
import com.umdc.smartkin.api.v1.to.NotificationResponse;
import com.umdc.smartkin.api.v1.to.PageResponse;
import com.umdc.smartkin.api.v1.to.UnreadCountResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/// REST controller for the notification inbox.
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController implements NotificationApi {

    private final NotificationService notificationService;

    /// Creates the controller.
    ///
    /// @param notificationService the service that backs the operations
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public NotificationService getService() {
        return notificationService;
    }

    @Override
    public ResponseEntity<PageResponse<NotificationResponse>> listNotifications(String token, boolean unread, int page, int size) {
        return notificationService.list(token, unread, page, size);
    }

    @Override
    public ResponseEntity<UnreadCountResponse> getUnreadNotificationCount(String token) {
        return notificationService.unreadCount(token);
    }

    @Override
    public ResponseEntity<Void> markAllNotificationsRead(String token) {
        return notificationService.markAllRead(token);
    }

    @Override
    public ResponseEntity<Void> markNotificationRead(String token, UUID notificationId) {
        return notificationService.markRead(token, notificationId);
    }
}
