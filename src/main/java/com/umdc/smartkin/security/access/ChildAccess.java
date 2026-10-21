package com.umdc.smartkin.security.access;

import com.umdc.smartkin.jpa.domain.ChildEntity;

/// The decision of [AccessPolicy] together with the child it was taken about.
///
/// @param decision the outcome
/// @param child    the child when the access is allowed, otherwise `null`
public record ChildAccess(ChildAccessDecision decision, ChildEntity child) {

    /// Tells whether the caller may reach the child.
    ///
    /// @return `true` for [ChildAccessDecision#OWNER] and [ChildAccessDecision#SELF]
    public boolean allowed() {
        return decision == ChildAccessDecision.OWNER || decision == ChildAccessDecision.SELF;
    }

    static ChildAccess denied(ChildAccessDecision decision) {
        return new ChildAccess(decision, null);
    }
}
