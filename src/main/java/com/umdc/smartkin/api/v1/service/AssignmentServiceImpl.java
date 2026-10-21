package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.AssignmentRequest;
import com.umdc.smartkin.api.v1.to.AssignmentResponse;
import com.umdc.smartkin.api.v1.to.PageResponse;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import com.umdc.smartkin.jpa.domain.ActivityProgressEntity;
import com.umdc.smartkin.jpa.domain.AssignedActivityEntity;
import com.umdc.smartkin.jpa.repository.ActivityProgressRepository;
import com.umdc.smartkin.jpa.repository.ActivityRepository;
import com.umdc.smartkin.jpa.repository.AssignedActivityRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import com.umdc.smartkin.kafka.event.DomainEventDispatcher;
import com.umdc.smartkin.kafka.event.payload.ActivityAssignedPayload;
import com.umdc.smartkin.mapper.AssignmentMapper;
import com.umdc.smartkin.security.access.AccessPolicy;
import com.umdc.smartkin.security.access.ChildAccess;
import com.umdc.smartkin.security.access.ChildAccessDecision;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Default implementation of [AssignmentService]. Who may reach which child is decided by [AccessPolicy]; a parent assigns only to their
/// own children, and an assignment that is not visible to the caller answers 404 so other families' ids stay hidden.
@Service
public class AssignmentServiceImpl implements AssignmentService {

    private final AccessPolicy accessPolicy;
    private final AssignedActivityRepository assignmentRepository;
    private final ActivityProgressRepository progressRepository;
    private final ActivityRepository activityRepository;
    private final ParentRepository parentRepository;
    private final DomainEventDispatcher events;
    private final AssignmentMapper assignmentMapper;
    private final Clock clock;

    /// Creates the service.
    ///
    /// @param accessPolicy         who may reach which child
    /// @param assignmentRepository the assignment repository
    /// @param progressRepository   the progress repository
    /// @param activityRepository   the activity repository
    /// @param parentRepository     the parent repository
    /// @param events               publishes `ACTIVITY_ASSIGNED` after the commit
    /// @param assignmentMapper     the mapper to the API representation
    /// @param clock                the current time
    public AssignmentServiceImpl(AccessPolicy accessPolicy, AssignedActivityRepository assignmentRepository,
                                 ActivityProgressRepository progressRepository, ActivityRepository activityRepository,
                                 ParentRepository parentRepository, DomainEventDispatcher events,
                                 AssignmentMapper assignmentMapper, Clock clock) {
        this.accessPolicy = accessPolicy;
        this.assignmentRepository = assignmentRepository;
        this.progressRepository = progressRepository;
        this.activityRepository = activityRepository;
        this.parentRepository = parentRepository;
        this.events = events;
        this.assignmentMapper = assignmentMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ResponseEntity<AssignmentResponse> assign(String token, AssignmentRequest request) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        var access = accessPolicy.manageChild(callerId, request.childId());
        if (!access.allowed()) {
            return denied(access, SmartKinAppConstants.NOT_A_PARENT_MESSAGE);
        }
        if (request.dueDate() != null && !request.dueDate().isAfter(clock.instant())) {
            return error(HttpStatus.BAD_REQUEST, SmartKinAppConstants.DUE_DATE_IN_PAST_MESSAGE);
        }
        var activity = activityRepository.findByIdAndIsActiveTrue(request.activityId());
        if (activity.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.ACTIVITY_NOT_FOUND_MESSAGE);
        }
        if (assignmentRepository.existsOpenAssignment(request.childId(), request.activityId())) {
            return error(HttpStatus.CONFLICT, SmartKinAppConstants.ASSIGNMENT_ALREADY_OPEN_MESSAGE);
        }
        var assignment = new AssignedActivityEntity();
        assignment.setChild(access.child());
        assignment.setActivity(activity.get());
        assignment.setAssignedBy(parentRepository.getReferenceById(callerId));
        assignment.setAssignedAt(clock.instant());
        assignment.setDueDate(request.dueDate());
        var saved = assignmentRepository.saveAndFlush(assignment);
        var payload = new ActivityAssignedPayload(saved.getId(), request.childId(), callerId, activity.get().getId(),
                activity.get().getTitle(), saved.getDueDate());
        events.activityAssigned(payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentMapper.toResponse(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<PageResponse<AssignmentResponse>> list(String token, UUID childId, int page, int size) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        if (page < 0 || size < 1 || size > SmartKinAppConstants.MAX_PAGE_SIZE) {
            return error(HttpStatus.BAD_REQUEST, SmartKinAppConstants.INVALID_PAGE_MESSAGE);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "assignedAt"));
        Page<AssignedActivityEntity> found;
        if (childId != null) {
            var access = accessPolicy.viewChild(callerId, childId);
            if (!access.allowed()) {
                return denied(access, SmartKinAppConstants.ACCESS_DENIED_MESSAGE);
            }
            found = assignmentRepository.findByChildId(childId, pageable);
        } else if (accessPolicy.isParent(callerId)) {
            found = assignmentRepository.findByChildParentId(callerId, pageable);
        } else if (accessPolicy.isChild(callerId)) {
            found = assignmentRepository.findByChildId(callerId, pageable);
        } else {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.NOT_A_PARENT_MESSAGE);
        }
        Map<UUID, ActivityProgressEntity> progress = progressRepository
                .findAllByAssignmentIdIn(found.getContent().stream().map(AssignedActivityEntity::getId).toList()).stream()
                .collect(Collectors.toMap(row -> row.getAssignment().getId(), Function.identity()));
        return ResponseEntity.ok(PageResponse.of(found, assignment -> toResponse(assignment, progress.get(assignment.getId()))));
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<AssignmentResponse> get(String token, UUID assignmentId) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        var assignment = assignmentRepository.findById(assignmentId);
        if (assignment.isEmpty() || !accessPolicy.viewChild(callerId, assignment.get().getChild().getId()).allowed()) {
            return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.ASSIGNMENT_NOT_FOUND_MESSAGE);
        }
        return ResponseEntity.ok(toResponse(assignment.get(), progressRepository.findByAssignmentId(assignmentId).orElse(null)));
    }

    private AssignmentResponse toResponse(AssignedActivityEntity assignment, ActivityProgressEntity progress) {
        return assignmentMapper.toResponse(assignment).withProgress(progress == null ? null : assignmentMapper.toResponse(progress));
    }

    private static <T> ResponseEntity<T> denied(ChildAccess access, String forbiddenMessage) {
        if (access.decision() == ChildAccessDecision.FORBIDDEN) {
            return error(HttpStatus.FORBIDDEN, forbiddenMessage);
        }
        return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.CHILD_NOT_FOUND_MESSAGE);
    }

    private static <T> ResponseEntity<T> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).header(SmartKinAppConstants.MESSAGE_ERROR_HEADER, message).build();
    }
}
