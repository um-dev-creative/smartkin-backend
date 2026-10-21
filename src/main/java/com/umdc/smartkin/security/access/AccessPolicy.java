package com.umdc.smartkin.security.access;

import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/// Who may reach which child. There is no role claim in the session token: a user is a parent when a `parent` row has their id
/// and a child when a `child` row does. A parent reaches only the children linked to them; a child reaches only themselves.
/// Every endpoint that touches a child (profiles, assignments, progress, quiz, points) asks this policy instead of re-implementing the rule.
@Component
public class AccessPolicy {

    private final ParentRepository parentRepository;
    private final ChildRepository childRepository;

    /// Creates the policy.
    ///
    /// @param parentRepository the parent repository
    /// @param childRepository  the child repository
    public AccessPolicy(ParentRepository parentRepository, ChildRepository childRepository) {
        this.parentRepository = parentRepository;
        this.childRepository = childRepository;
    }

    /// Tells whether the user has a parent profile.
    ///
    /// @param userId the user id
    /// @return `true` for a parent
    public boolean isParent(UUID userId) {
        return parentRepository.existsById(userId);
    }

    /// Tells whether the user has a child profile.
    ///
    /// @param userId the user id
    /// @return `true` for a child
    public boolean isChild(UUID userId) {
        return childRepository.existsById(userId);
    }

    /// Decides whether the caller may read a child: the child themselves or the parent they are linked to.
    ///
    /// @param callerId the caller's user id
    /// @param childId  the child's id
    /// @return the decision and, when allowed, the child
    public ChildAccess viewChild(UUID callerId, UUID childId) {
        if (callerId.equals(childId)) {
            return childRepository.findById(childId)
                    .map(child -> new ChildAccess(ChildAccessDecision.SELF, child))
                    .orElseGet(() -> ChildAccess.denied(ChildAccessDecision.NOT_VISIBLE));
        }
        return asParent(callerId, childId);
    }

    /// Decides whether the caller may change a child: only the parent the child is linked to (a child never manages anyone, themselves included).
    ///
    /// @param callerId the caller's user id
    /// @param childId  the child's id
    /// @return the decision and, when allowed, the child
    public ChildAccess manageChild(UUID callerId, UUID childId) {
        return asParent(callerId, childId);
    }

    private ChildAccess asParent(UUID callerId, UUID childId) {
        if (!isParent(callerId)) {
            return ChildAccess.denied(ChildAccessDecision.FORBIDDEN);
        }
        return childRepository.findById(childId)
                .filter(child -> isLinkedTo(child, callerId))
                .map(child -> new ChildAccess(ChildAccessDecision.OWNER, child))
                .orElseGet(() -> ChildAccess.denied(ChildAccessDecision.NOT_VISIBLE));
    }

    private static boolean isLinkedTo(ChildEntity child, UUID parentId) {
        return parentId.equals(child.getParent().getId());
    }
}
