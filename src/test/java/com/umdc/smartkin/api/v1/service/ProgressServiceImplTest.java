package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.FeedbackRequest;
import com.umdc.smartkin.api.v1.to.ProgressRequest;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import com.umdc.smartkin.jpa.domain.ActivityProgressEntity;
import com.umdc.smartkin.jpa.domain.AssignedActivityEntity;
import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.repository.ActivityProgressRepository;
import com.umdc.smartkin.jpa.repository.AssignedActivityRepository;
import com.umdc.smartkin.mapper.AssignmentMapperImpl;
import com.umdc.smartkin.security.access.AccessPolicy;
import com.umdc.smartkin.security.access.ChildAccess;
import com.umdc.smartkin.security.access.ChildAccessDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static com.umdc.smartkin.support.TestData.CHILD_ID;
import static com.umdc.smartkin.support.TestData.PARENT_ID;
import static com.umdc.smartkin.support.TestData.tokenFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private AccessPolicy accessPolicy;
    @Mock
    private AssignedActivityRepository assignmentRepository;
    @Mock
    private ActivityProgressRepository progressRepository;
    private ProgressServiceImpl service;
    private AssignedActivityEntity assignment;
    private ChildEntity child;

    @BeforeEach
    void setUp() {
        service = new ProgressServiceImpl(accessPolicy, assignmentRepository, progressRepository, new AssignmentMapperImpl(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        child = new ChildEntity();
        child.setId(CHILD_ID);
        assignment = new AssignedActivityEntity();
        assignment.setId(ASSIGNMENT_ID);
        assignment.setChild(child);
    }

    private void caller(UUID callerId, ChildAccessDecision decision) {
        when(accessPolicy.viewChild(callerId, CHILD_ID)).thenReturn(new ChildAccess(decision, decision == ChildAccessDecision.OWNER
                || decision == ChildAccessDecision.SELF ? child : null));
    }

    private ActivityProgressEntity row(int percentage) {
        var progress = new ActivityProgressEntity();
        progress.setAssignment(assignment);
        progress.setCompletionPercentage(percentage);
        progress.setStartTime(NOW.minusSeconds(600));
        progress.setLastAccessed(NOW.minusSeconds(300));
        return progress;
    }

    private void saveReturnsItsArgument() {
        when(progressRepository.save(any(ActivityProgressEntity.class))).thenAnswer(call -> call.getArgument(0));
    }

    // get

    @Test
    void getReturnsTheStoredProgressToTheParentAndToTheChild() {
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        caller(PARENT_ID, ChildAccessDecision.OWNER);
        caller(CHILD_ID, ChildAccessDecision.SELF);
        var stored = row(60);
        stored.setScore(90);
        stored.setParentFeedback("Keep going");
        when(progressRepository.findByAssignmentId(ASSIGNMENT_ID)).thenReturn(Optional.of(stored));

        var forParent = service.get(tokenFor(PARENT_ID), ASSIGNMENT_ID);
        var forChild = service.get(tokenFor(CHILD_ID), ASSIGNMENT_ID);

        assertEquals(HttpStatus.OK, forParent.getStatusCode());
        assertEquals(60, forParent.getBody().completionPercentage());
        assertEquals(90, forParent.getBody().score());
        assertEquals("Keep going", forChild.getBody().parentFeedback());
        assertEquals(ASSIGNMENT_ID, forChild.getBody().assignmentId());
    }

    @Test
    void getReturnsZeroBeforeTheChildStarts() {
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        caller(PARENT_ID, ChildAccessDecision.OWNER);
        when(progressRepository.findByAssignmentId(ASSIGNMENT_ID)).thenReturn(Optional.empty());

        var response = service.get(tokenFor(PARENT_ID), ASSIGNMENT_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().completionPercentage());
        assertNull(response.getBody().startTime());
        assertNull(response.getBody().completionTime());
        assertEquals(ASSIGNMENT_ID, response.getBody().assignmentId());
    }

    @Test
    void getHidesMissingForeignAndUnknownCallers() {
        var other = UUID.randomUUID();
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        caller(other, ChildAccessDecision.NOT_VISIBLE);
        var missingId = UUID.randomUUID();
        when(assignmentRepository.findById(missingId)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, service.get(tokenFor(other), ASSIGNMENT_ID).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, service.get(tokenFor(PARENT_ID), missingId).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, service.get("garbage", ASSIGNMENT_ID).getStatusCode());
    }

    // update

    @Test
    void theFirstReportCreatesTheRowAndSetsTheStartTime() {
        when(assignmentRepository.findForUpdateById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        caller(CHILD_ID, ChildAccessDecision.SELF);
        when(progressRepository.findByAssignmentId(ASSIGNMENT_ID)).thenReturn(Optional.empty());
        saveReturnsItsArgument();

        var response = service.update(tokenFor(CHILD_ID), ASSIGNMENT_ID, new ProgressRequest(30));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(30, response.getBody().completionPercentage());
        assertEquals(NOW, response.getBody().startTime());
        assertEquals(NOW, response.getBody().lastAccessed());
        assertNull(response.getBody().completionTime());
    }

    @Test
    void laterReportsKeepTheStartTimeAndMoveTheLastAccess() {
        var stored = row(30);
        when(assignmentRepository.findForUpdateById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        caller(CHILD_ID, ChildAccessDecision.SELF);
        when(progressRepository.findByAssignmentId(ASSIGNMENT_ID)).thenReturn(Optional.of(stored));
        saveReturnsItsArgument();

        var response = service.update(tokenFor(CHILD_ID), ASSIGNMENT_ID, new ProgressRequest(30));

        assertEquals(30, response.getBody().completionPercentage());
        assertEquals(NOW.minusSeconds(600), response.getBody().startTime());
        assertEquals(NOW, response.getBody().lastAccessed());
    }

    @Test
    void reaching100CompletesItOnceAndNeverGoesBackwards() {
        var stored = row(80);
        when(assignmentRepository.findForUpdateById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        caller(CHILD_ID, ChildAccessDecision.SELF);
        when(progressRepository.findByAssignmentId(ASSIGNMENT_ID)).thenReturn(Optional.of(stored));
        saveReturnsItsArgument();

        var done = service.update(tokenFor(CHILD_ID), ASSIGNMENT_ID, new ProgressRequest(100));
        stored.setCompletionTime(NOW.minusSeconds(10));
        var again = service.update(tokenFor(CHILD_ID), ASSIGNMENT_ID, new ProgressRequest(100));
        var back = service.update(tokenFor(CHILD_ID), ASSIGNMENT_ID, new ProgressRequest(50));

        assertEquals(NOW, done.getBody().completionTime());
        assertEquals(NOW.minusSeconds(10), again.getBody().completionTime());
        assertEquals(HttpStatus.BAD_REQUEST, back.getStatusCode());
        assertEquals(SmartKinAppConstants.PROGRESS_CANNOT_DECREASE_MESSAGE, back.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
    }

    @Test
    void aDecreaseIsRejectedWithoutSaving() {
        when(assignmentRepository.findForUpdateById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        caller(CHILD_ID, ChildAccessDecision.SELF);
        when(progressRepository.findByAssignmentId(ASSIGNMENT_ID)).thenReturn(Optional.of(row(70)));

        var response = service.update(tokenFor(CHILD_ID), ASSIGNMENT_ID, new ProgressRequest(10));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(progressRepository, never()).save(any());
    }

    @Test
    void onlyTheAssignedChildReportsProgress() {
        var other = UUID.randomUUID();
        when(assignmentRepository.findForUpdateById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        caller(PARENT_ID, ChildAccessDecision.OWNER);
        caller(other, ChildAccessDecision.FORBIDDEN);
        var missingId = UUID.randomUUID();
        when(assignmentRepository.findForUpdateById(missingId)).thenReturn(Optional.empty());

        var parent = service.update(tokenFor(PARENT_ID), ASSIGNMENT_ID, new ProgressRequest(10));
        var sibling = service.update(tokenFor(other), ASSIGNMENT_ID, new ProgressRequest(10));
        var missing = service.update(tokenFor(CHILD_ID), missingId, new ProgressRequest(10));
        var unknown = service.update(null, ASSIGNMENT_ID, new ProgressRequest(10));

        assertEquals(HttpStatus.FORBIDDEN, parent.getStatusCode());
        assertEquals(SmartKinAppConstants.ONLY_THE_CHILD_MESSAGE, parent.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
        assertEquals(HttpStatus.NOT_FOUND, sibling.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, unknown.getStatusCode());
        verify(progressRepository, never()).save(any());
    }

    // feedback

    @Test
    void theParentLeavesFeedbackOnAnExistingProgress() {
        var stored = row(100);
        when(assignmentRepository.findForUpdateById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        caller(PARENT_ID, ChildAccessDecision.OWNER);
        when(progressRepository.findByAssignmentId(ASSIGNMENT_ID)).thenReturn(Optional.of(stored));
        saveReturnsItsArgument();

        var response = service.feedback(tokenFor(PARENT_ID), ASSIGNMENT_ID, new FeedbackRequest("Great job"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Great job", response.getBody().parentFeedback());
        assertEquals(100, response.getBody().completionPercentage());
    }

    @Test
    void feedbackBeforeTheChildStartsCreatesAnEmptyRow() {
        when(assignmentRepository.findForUpdateById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        caller(PARENT_ID, ChildAccessDecision.OWNER);
        when(progressRepository.findByAssignmentId(ASSIGNMENT_ID)).thenReturn(Optional.empty());
        var saved = ArgumentCaptor.forClass(ActivityProgressEntity.class);
        when(progressRepository.save(saved.capture())).thenAnswer(call -> call.getArgument(0));

        var response = service.feedback(tokenFor(PARENT_ID), ASSIGNMENT_ID, new FeedbackRequest("Take your time"));

        assertEquals(0, response.getBody().completionPercentage());
        assertNull(response.getBody().startTime());
        assertEquals("Take your time", saved.getValue().getParentFeedback());
    }

    @Test
    void onlyTheAssigningParentLeavesFeedback() {
        var other = UUID.randomUUID();
        when(assignmentRepository.findForUpdateById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        caller(CHILD_ID, ChildAccessDecision.SELF);
        caller(other, ChildAccessDecision.NOT_VISIBLE);
        var missingId = UUID.randomUUID();
        when(assignmentRepository.findForUpdateById(missingId)).thenReturn(Optional.empty());

        var byChild = service.feedback(tokenFor(CHILD_ID), ASSIGNMENT_ID, new FeedbackRequest("x"));
        var byOther = service.feedback(tokenFor(other), ASSIGNMENT_ID, new FeedbackRequest("x"));
        var missing = service.feedback(tokenFor(PARENT_ID), missingId, new FeedbackRequest("x"));
        var unknown = service.feedback("garbage", ASSIGNMENT_ID, new FeedbackRequest("x"));

        assertEquals(HttpStatus.FORBIDDEN, byChild.getStatusCode());
        assertEquals(SmartKinAppConstants.ONLY_THE_PARENT_MESSAGE, byChild.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
        assertEquals(HttpStatus.NOT_FOUND, byOther.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, unknown.getStatusCode());
        verify(progressRepository, never()).save(any());
    }
}
