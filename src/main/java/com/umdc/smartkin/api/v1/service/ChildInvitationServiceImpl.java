package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.AcceptInvitationRequest;
import com.umdc.smartkin.api.v1.to.ChildResponse;
import com.umdc.smartkin.api.v1.to.InvitationRequest;
import com.umdc.smartkin.api.v1.to.InvitationResponse;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import com.umdc.smartkin.invitation.InvitationCode;
import com.umdc.smartkin.jpa.domain.ChildInvitationEntity;
import com.umdc.smartkin.jpa.domain.InvitationStatus;
import com.umdc.smartkin.jpa.repository.ChildInvitationRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import com.umdc.smartkin.mapper.ChildMapper;
import com.umdc.smartkin.security.access.AccessPolicy;
import com.umdc.smartkin.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/// Default implementation of [ChildInvitationService]. Accepting runs in one transaction: the invitation is claimed with a single conditional update
/// and the child profile is created; if the profile cannot be created the claim is rolled back and the code stays usable.
@Service
public class ChildInvitationServiceImpl implements ChildInvitationService {

    private static final Logger logger = LoggerFactory.getLogger(ChildInvitationServiceImpl.class);
    private static final String EXPIRED = "EXPIRED";
    private static final int MAX_CODE_DRAWS = 5;

    private final ParentRepository parentRepository;
    private final ChildInvitationRepository invitationRepository;
    private final ChildLinker childLinker;
    private final ChildMapper childMapper;
    private final AccessPolicy accessPolicy;
    private final TransactionTemplate transaction;
    private final Clock clock;

    /// Creates the service.
    ///
    /// @param parentRepository     the parent repository
    /// @param invitationRepository the invitation repository
    /// @param childLinker          creates the child profile
    /// @param childMapper          the entity mapper
    /// @param accessPolicy         tells who is a parent or a child
    /// @param transaction          wraps the accept operation so a failure rolls the claim back
    /// @param clock                the current time
    public ChildInvitationServiceImpl(ParentRepository parentRepository, ChildInvitationRepository invitationRepository,
                                      ChildLinker childLinker, ChildMapper childMapper, AccessPolicy accessPolicy,
                                      TransactionTemplate transaction, Clock clock) {
        this.parentRepository = parentRepository;
        this.invitationRepository = invitationRepository;
        this.childLinker = childLinker;
        this.childMapper = childMapper;
        this.accessPolicy = accessPolicy;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public ResponseEntity<InvitationResponse> create(String token, InvitationRequest request) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        var parent = parentRepository.findById(callerId);
        if (parent.isEmpty()) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.NOT_A_PARENT_MESSAGE);
        }
        var now = clock.instant();
        if (invitationRepository.countByParentIdAndStatusAndExpiresAtAfter(callerId, InvitationStatus.PENDING, now)
                >= SmartKinAppConstants.MAX_PENDING_INVITATIONS) {
            return error(HttpStatus.CONFLICT, SmartKinAppConstants.INVITATION_LIMIT_MESSAGE);
        }
        var code = drawFreeCode();
        if (code.isEmpty()) {
            logger.error("No free invitation code after {} draws", MAX_CODE_DRAWS);
            return error(HttpStatus.CONFLICT, SmartKinAppConstants.INVITATION_CODE_ATTEMPTS_MESSAGE);
        }
        var invitation = new ChildInvitationEntity();
        invitation.setParent(parent.get());
        invitation.setCodeHash(InvitationCode.hash(code.get()));
        invitation.setGradeLevel(request.gradeLevel());
        invitation.setAvatar(request.avatar());
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(now.plus(Duration.ofDays(SmartKinAppConstants.INVITATION_VALID_DAYS)));
        var saved = invitationRepository.save(invitation);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved, InvitationCode.format(code.get()), now));
    }

    private Optional<String> drawFreeCode() {
        return Stream.generate(InvitationCode::generate).limit(MAX_CODE_DRAWS)
                .filter(code -> !invitationRepository.existsByCodeHash(InvitationCode.hash(code))).findFirst();
    }

    @Override
    public ResponseEntity<List<InvitationResponse>> list(String token) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        if (!accessPolicy.isParent(callerId)) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.NOT_A_PARENT_MESSAGE);
        }
        var now = clock.instant();
        return ResponseEntity.ok(invitationRepository.findAllByParentIdOrderByCreatedAtDesc(callerId).stream()
                .map(invitation -> toResponse(invitation, null, now)).toList());
    }

    @Override
    public ResponseEntity<Void> revoke(String token, UUID invitationId) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        if (!accessPolicy.isParent(callerId)) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.NOT_A_PARENT_MESSAGE);
        }
        var invitation = invitationRepository.findByIdAndParentId(invitationId, callerId);
        if (invitation.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.INVITATION_NOT_FOUND_MESSAGE);
        }
        var found = invitation.get();
        if (found.getStatus() == InvitationStatus.ACCEPTED) {
            return error(HttpStatus.CONFLICT, SmartKinAppConstants.INVITATION_ALREADY_ACCEPTED_MESSAGE);
        }
        found.setStatus(InvitationStatus.REVOKED);
        invitationRepository.save(found);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ChildResponse> accept(String token, AcceptInvitationRequest request) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        if (!childLinker.isFree(callerId)) {
            return error(HttpStatus.CONFLICT, SmartKinAppConstants.CHILD_ALREADY_LINKED_MESSAGE);
        }
        var code = InvitationCode.normalize(request.code());
        if (code.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.INVITATION_NOT_FOUND_MESSAGE);
        }
        var hash = InvitationCode.hash(code.get());
        try {
            return transaction.execute(status -> claimAndLink(hash, callerId));
        } catch (DataIntegrityViolationException e) {
            logger.warn("The user {} became a family member while accepting an invitation: {}", callerId, e.getMessage());
            return error(HttpStatus.CONFLICT, SmartKinAppConstants.CHILD_ALREADY_LINKED_MESSAGE);
        }
    }

    private ResponseEntity<ChildResponse> claimAndLink(String codeHash, UUID callerId) {
        var invitation = invitationRepository.findByCodeHash(codeHash);
        if (invitation.isEmpty() || invitationRepository.claim(invitation.get().getId(), callerId, clock.instant()) == 0) {
            return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.INVITATION_NOT_FOUND_MESSAGE);
        }
        var found = invitation.get();
        var child = childLinker.create(found.getParent(), callerId, found.getGradeLevel(), found.getAvatar());
        return ResponseEntity.status(HttpStatus.CREATED).body(childMapper.toResponse(child));
    }

    private static InvitationResponse toResponse(ChildInvitationEntity invitation, String code, Instant now) {
        var status = invitation.getStatus() == InvitationStatus.PENDING && !invitation.getExpiresAt().isAfter(now)
                ? EXPIRED : invitation.getStatus().name();
        return new InvitationResponse(invitation.getId(), code, status, invitation.getGradeLevel(), invitation.getAvatar(),
                invitation.getExpiresAt(), invitation.getCreatedAt());
    }

    private static <T> ResponseEntity<T> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).header(SmartKinAppConstants.MESSAGE_ERROR_HEADER, message).build();
    }
}
