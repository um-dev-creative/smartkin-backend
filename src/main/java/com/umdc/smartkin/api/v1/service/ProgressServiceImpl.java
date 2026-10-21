package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.FeedbackRequest;
import com.umdc.smartkin.api.v1.to.ProgressRequest;
import com.umdc.smartkin.api.v1.to.ProgressResponse;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import com.umdc.smartkin.jpa.domain.ActivityProgressEntity;
import com.umdc.smartkin.jpa.domain.AssignedActivityEntity;
import com.umdc.smartkin.jpa.repository.ActivityProgressRepository;
import com.umdc.smartkin.jpa.repository.AssignedActivityRepository;
import com.umdc.smartkin.mapper.AssignmentMapper;
import com.umdc.smartkin.security.access.AccessPolicy;
import com.umdc.smartkin.security.access.ChildAccessDecision;
import com.umdc.smartkin.util.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/// Default implementation of [ProgressService]. The assignment is read with a write lock when it is changed, so concurrent updates of the
/// same assignment are serialized. An assignment the caller cannot reach answers 404.
@Service
public class ProgressServiceImpl implements ProgressService {

    private static final int COMPLETE = 100;

    private final AccessPolicy accessPolicy;
    private final AssignedActivityRepository assignmentRepository;
    private final ActivityProgressRepository progressRepository;
    private final AssignmentMapper assignmentMapper;
    private final Clock clock;

    /// Creates the service.
    ///
    /// @param accessPolicy         who may reach which child
    /// @param assignmentRepository the assignment repository
    /// @param progressRepository   the progress repository
    /// @param assignmentMapper     the mapper to the API representation
    /// @param clock                the current time
    public ProgressServiceImpl(AccessPolicy accessPolicy, AssignedActivityRepository assignmentRepository,
                               ActivityProgressRepository progressRepository, AssignmentMapper assignmentMapper, Clock clock) {
        this.accessPolicy = accessPolicy;
        this.assignmentRepository = assignmentRepository;
        this.progressRepository = progressRepository;
        this.assignmentMapper = assignmentMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<ProgressResponse> get(String token, UUID assignmentId) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        var assignment = assignmentRepository.findById(assignmentId);
        if (assignment.isEmpty() || !accessPolicy.viewChild(callerId, assignment.get().getChild().getId()).allowed()) {
            return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.ASSIGNMENT_NOT_FOUND_MESSAGE);
        }
        return ResponseEntity.ok(progressRepository.findByAssignmentId(assignmentId).map(assignmentMapper::toResponse)
                .orElseGet(() -> notStarted(assignmentId)));
    }

    @Override
    @Transactional
    public ResponseEntity<ProgressResponse> update(String token, UUID assignmentId, ProgressRequest request) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        var assignment = assignmentRepository.findForUpdateById(assignmentId);
        if (assignment.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.ASSIGNMENT_NOT_FOUND_MESSAGE);
        }
        var decision = accessPolicy.viewChild(callerId, assignment.get().getChild().getId()).decision();
        if (decision == ChildAccessDecision.OWNER) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.ONLY_THE_CHILD_MESSAGE);
        }
        if (decision != ChildAccessDecision.SELF) {
            return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.ASSIGNMENT_NOT_FOUND_MESSAGE);
        }
        var now = clock.instant();
        var progress = progressRepository.findByAssignmentId(assignmentId).orElseGet(() -> started(assignment.get(), now));
        var percentage = request.completionPercentage();
        if (percentage < progress.getCompletionPercentage()) {
            return error(HttpStatus.BAD_REQUEST, SmartKinAppConstants.PROGRESS_CANNOT_DECREASE_MESSAGE);
        }
        progress.setCompletionPercentage(percentage);
        progress.setLastAccessed(now);
        if (percentage == COMPLETE && progress.getCompletionTime() == null) {
            progress.setCompletionTime(now);
        }
        return ResponseEntity.ok(assignmentMapper.toResponse(progressRepository.save(progress)));
    }

    @Override
    @Transactional
    public ResponseEntity<ProgressResponse> feedback(String token, UUID assignmentId, FeedbackRequest request) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        var assignment = assignmentRepository.findForUpdateById(assignmentId);
        if (assignment.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.ASSIGNMENT_NOT_FOUND_MESSAGE);
        }
        var decision = accessPolicy.viewChild(callerId, assignment.get().getChild().getId()).decision();
        if (decision == ChildAccessDecision.SELF) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.ONLY_THE_PARENT_MESSAGE);
        }
        if (decision != ChildAccessDecision.OWNER) {
            return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.ASSIGNMENT_NOT_FOUND_MESSAGE);
        }
        var progress = progressRepository.findByAssignmentId(assignmentId).orElseGet(() -> notStartedRow(assignment.get()));
        progress.setParentFeedback(request.parentFeedback());
        return ResponseEntity.ok(assignmentMapper.toResponse(progressRepository.save(progress)));
    }

    private static ActivityProgressEntity started(AssignedActivityEntity assignment, Instant now) {
        var progress = notStartedRow(assignment);
        progress.setStartTime(now);
        return progress;
    }

    private static ActivityProgressEntity notStartedRow(AssignedActivityEntity assignment) {
        var progress = new ActivityProgressEntity();
        progress.setAssignment(assignment);
        progress.setCompletionPercentage(0);
        return progress;
    }

    private static ProgressResponse notStarted(UUID assignmentId) {
        return new ProgressResponse(assignmentId, 0, null, null, null, null, null);
    }

    private static <T> ResponseEntity<T> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).header(SmartKinAppConstants.MESSAGE_ERROR_HEADER, message).build();
    }
}
