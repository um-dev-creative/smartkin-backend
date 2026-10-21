package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.NotificationService;
import com.umdc.smartkin.api.v1.to.NotificationResponse;
import com.umdc.smartkin.api.v1.to.PageResponse;
import com.umdc.smartkin.api.v1.to.UnreadCountResponse;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

import static com.umdc.security.constant.ConstantApp.SESSION_TOKEN_KEY;

/// Notifications API: the inbox of the authenticated user.
@Tag(name = "notifications", description = "The notifications API")
public interface NotificationApi {

    /// Provides the service that backs the operations.
    ///
    /// @return the notification service
    default NotificationService getService() {
        return new NotificationService() {};
    }

    /// Lists the caller's notifications.
    ///
    /// @param token  the session token
    /// @param unread only the unread ones
    /// @param page   0-based page
    /// @param size   page size, 1 to 100
    /// @return the page of notifications
    @Operation(operationId = "listNotifications", summary = "Inbox of the caller")
    @ApiResponses({
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_OK, description = "Notifications"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_FORBIDDEN, description = SmartKinAppConstants.FORBIDDEN_DESCRIPTION, content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<PageResponse<NotificationResponse>> listNotifications(
            @Parameter(description = SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return getService().list(token, unread, page, size);
    }

    /// Counts the caller's unread notifications.
    ///
    /// @param token the session token
    /// @return the count
    @Operation(operationId = "getUnreadNotificationCount", summary = "Number of unread notifications of the caller")
    @ApiResponses({
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_OK, description = "Count"),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_FORBIDDEN, description = SmartKinAppConstants.FORBIDDEN_DESCRIPTION, content = @Content)
    })
    @GetMapping(value = "/unread-count", produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<UnreadCountResponse> getUnreadNotificationCount(
            @Parameter(description = SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token) {
        return getService().unreadCount(token);
    }

    /// Marks every notification of the caller as read.
    ///
    /// @param token the session token
    /// @return 204
    @Operation(operationId = "markAllNotificationsRead", summary = "Mark every notification of the caller as read")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Done", content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_FORBIDDEN, description = SmartKinAppConstants.FORBIDDEN_DESCRIPTION, content = @Content)
    })
    @PostMapping("/read-all")
    default ResponseEntity<Void> markAllNotificationsRead(
            @Parameter(description = SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token) {
        return getService().markAllRead(token);
    }

    /// Marks one notification of the caller as read.
    ///
    /// @param token          the session token
    /// @param notificationId the notification's id
    /// @return 204
    @Operation(operationId = "markNotificationRead", summary = "Mark one notification as read")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Done", content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_FORBIDDEN, description = SmartKinAppConstants.FORBIDDEN_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_NOT_FOUND, description = SmartKinAppConstants.NOT_FOUND_DESCRIPTION, content = @Content)
    })
    @PostMapping("/{notificationId}/read")
    default ResponseEntity<Void> markNotificationRead(
            @Parameter(description = SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @NotNull @PathVariable UUID notificationId) {
        return getService().markRead(token, notificationId);
    }
}
