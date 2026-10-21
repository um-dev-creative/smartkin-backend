package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.NotificationResponse;
import com.umdc.smartkin.api.v1.to.PageResponse;
import com.umdc.smartkin.api.v1.to.UnreadCountResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/// The inbox of the caller. Every notification belongs to one user and only that user can see or change it.
public interface NotificationService {

    /// Lists the caller's notifications, newest first.
    ///
    /// @param token  the session token of the caller
    /// @param unread only the unread ones
    /// @param page   0-based page
    /// @param size   page size, 1 to 100
    /// @return 200 with the page; 400 for a bad page or size; 403 when the token has no user
    default ResponseEntity<PageResponse<NotificationResponse>> list(String token, boolean unread, int page, int size) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Counts the caller's unread notifications.
    ///
    /// @param token the session token of the caller
    /// @return 200 with the count; 403 when the token has no user
    default ResponseEntity<UnreadCountResponse> unreadCount(String token) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Marks one notification of the caller as read (idempotent: the first read time is kept).
    ///
    /// @param token          the session token of the caller
    /// @param notificationId the notification's id
    /// @return 204; 403 when the token has no user; 404 when it does not exist or belongs to someone else
    default ResponseEntity<Void> markRead(String token, UUID notificationId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Marks every notification of the caller as read.
    ///
    /// @param token the session token of the caller
    /// @return 204; 403 when the token has no user
    default ResponseEntity<Void> markAllRead(String token) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
