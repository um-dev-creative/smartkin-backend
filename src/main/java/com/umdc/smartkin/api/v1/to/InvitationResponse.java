package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/// An invitation of a parent. The `code` is returned only when the invitation is created: it is not stored and cannot be read again.
///
/// @param invitationId the invitation id
/// @param code         the one-time code (`ABCD-EFGH`), only on creation
/// @param status       `PENDING`, `ACCEPTED`, `REVOKED` or `EXPIRED`
/// @param gradeLevel   school grade the child will get
/// @param avatar       avatar the child will get
/// @param expiresAt    when the code stops working
/// @param createdAt    when the invitation was created
@Schema(name = "Invitation")
public record InvitationResponse(UUID invitationId, String code, String status, String gradeLevel, String avatar,
                                 Instant expiresAt, Instant createdAt) {
}
