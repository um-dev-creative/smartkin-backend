package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.ChildInvitationEntity;
import com.umdc.smartkin.jpa.domain.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Repository of the invitations a parent sends to future children.
public interface ChildInvitationRepository extends JpaRepository<ChildInvitationEntity, UUID> {

    /// The invitation whose code has that SHA-256.
    Optional<ChildInvitationEntity> findByCodeHash(String codeHash);

    /// Whether a code with that SHA-256 already exists (codes are random, a clash only means "draw another").
    boolean existsByCodeHash(String codeHash);

    /// The invitations of a parent, newest first.
    List<ChildInvitationEntity> findAllByParentIdOrderByCreatedAtDesc(UUID parentId);

    /// An invitation, only if it belongs to the parent.
    Optional<ChildInvitationEntity> findByIdAndParentId(UUID id, UUID parentId);

    /// How many invitations of the parent are in that status and not yet expired.
    long countByParentIdAndStatusAndExpiresAtAfter(UUID parentId, InvitationStatus status, Instant now);

    /// Uses a pending, unexpired invitation in one atomic statement, so two users cannot accept the same code.
    ///
    /// @param id         the invitation
    /// @param acceptedBy the user who accepts it
    /// @param now        the current instant
    /// @return 1 when the invitation was claimed, 0 when it is not pending any more or has expired
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("update ChildInvitationEntity i set i.status = com.umdc.smartkin.jpa.domain.InvitationStatus.ACCEPTED, "
            + "i.acceptedBy = :acceptedBy, i.acceptedAt = :now "
            + "where i.id = :id and i.status = com.umdc.smartkin.jpa.domain.InvitationStatus.PENDING and i.expiresAt > :now")
    int claim(@Param("id") UUID id, @Param("acceptedBy") UUID acceptedBy, @Param("now") Instant now);
}
