package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.NotificationResponse;
import com.umdc.smartkin.api.v1.to.PageResponse;
import com.umdc.smartkin.api.v1.to.UnreadCountResponse;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import com.umdc.smartkin.jpa.domain.NotificationEntity;
import com.umdc.smartkin.jpa.repository.NotificationRepository;
import com.umdc.smartkin.mapper.NotificationMapper;
import com.umdc.smartkin.util.JwtUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/// Default implementation of [NotificationService]. The caller is the `uid` of the session token, which is the `user_id` of the
/// notifications: a user never reaches someone else's inbox, and asking for another user's notification answers 404 as if it did not exist.
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final Clock clock;

    /// Creates the service.
    ///
    /// @param notificationRepository the notification repository
    /// @param notificationMapper     the mapper to the API representation
    /// @param clock                  the current time
    public NotificationServiceImpl(NotificationRepository notificationRepository, NotificationMapper notificationMapper, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<PageResponse<NotificationResponse>> list(String token, boolean unread, int page, int size) {
        var userId = JwtUtil.getUidFromToken(token);
        if (userId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        if (page < 0 || size < 1 || size > SmartKinAppConstants.MAX_PAGE_SIZE) {
            return error(HttpStatus.BAD_REQUEST, SmartKinAppConstants.INVALID_PAGE_MESSAGE);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationEntity> found = unread
                ? notificationRepository.findByUserIdAndIsReadFalse(userId, pageable)
                : notificationRepository.findByUserId(userId, pageable);
        return ResponseEntity.ok(PageResponse.of(found, notificationMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<UnreadCountResponse> unreadCount(String token) {
        var userId = JwtUtil.getUidFromToken(token);
        if (userId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        return ResponseEntity.ok(new UnreadCountResponse(notificationRepository.countByUserIdAndIsReadFalse(userId)));
    }

    @Override
    @Transactional
    public ResponseEntity<Void> markRead(String token, UUID notificationId) {
        var userId = JwtUtil.getUidFromToken(token);
        if (userId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        var notification = notificationRepository.findByIdAndUserId(notificationId, userId);
        if (notification.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.NOTIFICATION_NOT_FOUND_MESSAGE);
        }
        if (!Boolean.TRUE.equals(notification.get().getIsRead())) {
            notification.get().setIsRead(true);
            notification.get().setReadAt(clock.instant());
            notificationRepository.save(notification.get());
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> markAllRead(String token) {
        var userId = JwtUtil.getUidFromToken(token);
        if (userId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        notificationRepository.markAllRead(userId, clock.instant());
        return ResponseEntity.noContent().build();
    }

    private static <T> ResponseEntity<T> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).header(SmartKinAppConstants.MESSAGE_ERROR_HEADER, message).build();
    }
}
