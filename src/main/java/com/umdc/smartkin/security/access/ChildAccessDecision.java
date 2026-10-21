package com.umdc.smartkin.security.access;

/// Outcome of asking whether a caller may reach a child.
public enum ChildAccessDecision {
    /// The caller is the parent the child is linked to.
    OWNER,
    /// The caller is the child.
    SELF,
    /// The child does not exist or belongs to another family: answer 404 so other families' ids are not revealed.
    NOT_VISIBLE,
    /// The caller has no right to this kind of access at all: answer 403.
    FORBIDDEN
}
