package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

/// How many notifications the user has not read yet.
///
/// @param count the number of unread notifications
@Schema(name = "UnreadCount")
public record UnreadCountResponse(long count) {
}
