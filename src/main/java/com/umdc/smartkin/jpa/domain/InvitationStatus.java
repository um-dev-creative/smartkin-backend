package com.umdc.smartkin.jpa.domain;

/// Life cycle of a [ChildInvitationEntity]. An invitation that is still `PENDING` after its expiry is reported as expired by the API but never rewritten.
public enum InvitationStatus {
    /// Created and not yet used.
    PENDING,
    /// Used by a user, who became the parent's child.
    ACCEPTED,
    /// Cancelled by the parent.
    REVOKED
}
