package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.AssignmentRequest;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import com.umdc.smartkin.jpa.domain.ActivityEntity;
import com.umdc.smartkin.jpa.domain.ActivityProgressEntity;
import com.umdc.smartkin.jpa.domain.AssignedActivityEntity;
import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import com.umdc.smartkin.jpa.domain.TopicEntity;
import com.umdc.smartkin.jpa.repository.ActivityProgressRepository;
import com.umdc.smartkin.jpa.repository.ActivityRepository;
import com.umdc.smartkin.jpa.repository.AssignedActivityRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import com.umdc.smartkin.kafka.event.SmartKinEventPublisher;
import com.umdc.smartkin.kafka.event.payload.ActivityAssignedPayload;
import com.umdc.smartkin.mapper.AssignmentMapperImpl;
import com.umdc.smartkin.security.access.AccessPolicy;
import com.umdc.smartkin.security.access.ChildAccess;
import com.umdc.smartkin.security.access.ChildAccessDecision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.umdc.smartkin.support.TestData.ACTIVITY_ID;
import static com.umdc.smartkin.support.TestData.CHILD_ID;
import static com.umdc.smartkin.support.TestData.LESSON_ID;
import static com.umdc.smartkin.support.TestData.PARENT_ID;
import static com.umdc.smartkin.support.TestData.TOPIC_ID;
import static com.umdc.smartkin.support.TestData.tokenFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private AccessPolicy accessPolicy;
    @Mock
    private AssignedActivityRepository assignmentRepository;
    @Mock
    private ActivityProgressRepository progressRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ParentRepository parentRepository;
    @Mock
    private SmartKinEventPublisher eventPublisher;
    private AssignmentServiceImpl service;
    private ChildEntity child;
    private ParentEntity parent;
    private ActivityEntity activity;

    @BeforeEach
    void setUp() {
        service = new AssignmentServiceImpl(accessPolicy, assignmentRepository, progressRepository, activityRepository, parentRepository,
                eventPublisher, new AssignmentMapperImpl(), Clock.fixed(NOW, ZoneOffset.UTC));
        parent = new ParentEntity();
        parent.setId(PARENT_ID);
        child = new ChildEntity();
        child.setId(CHILD_ID);
        child.setParent(parent);
        var topic = new TopicEntity();
        topic.setId(TOPIC_ID);
        activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID);
        activity.setTopic(topic);
        activity.setTitle("Passwords");
        activity.setLessonId(LESSON_ID);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private AssignedActivityEntity assignment() {
        var assignment = new AssignedActivityEntity();
        assignment.setId(ASSIGNMENT_ID);
        assignment.setChild(child);
        assignment.setActivity(activity);
        assignment.setAssignedBy(parent);
        assignment.setAssignedAt(NOW);
        return assignment;
    }

    private void parentOwnsChild() {
        when(accessPolicy.manageChild(PARENT_ID, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.OWNER, child));
    }

    private void saveReturnsTheAssignment() {
        when(parentRepository.getReferenceById(PARENT_ID)).thenReturn(parent);
        when(assignmentRepository.saveAndFlush(any(AssignedActivityEntity.class))).thenAnswer(call -> {
            AssignedActivityEntity entity = call.getArgument(0);
            entity.setId(ASSIGNMENT_ID);
            return entity;
        });
    }

    // assign

    @Test
    void assignCreatesTheAssignmentAndPublishesTheEvent() {
        parentOwnsChild();
        when(activityRepository.findByIdAndIsActiveTrue(ACTIVITY_ID)).thenReturn(Optional.of(activity));
        when(assignmentRepository.existsOpenAssignment(CHILD_ID, ACTIVITY_ID)).thenReturn(false);
        saveReturnsTheAssignment();
        var due = NOW.plusSeconds(3600);

        var response = service.assign(tokenFor(PARENT_ID), new AssignmentRequest(CHILD_ID, ACTIVITY_ID, due));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        var body = response.getBody();
        assertEquals(ASSIGNMENT_ID, body.assignmentId());
        assertEquals(CHILD_ID, body.childId());
        assertEquals(ACTIVITY_ID, body.activityId());
        assertEquals("Passwords", body.activityTitle());
        assertEquals(LESSON_ID, body.lessonId());
        assertEquals(PARENT_ID, body.assignedBy());
        assertEquals(NOW, body.assignedAt());
        assertEquals(due, body.dueDate());
        assertNull(body.progress());
        var payload = ArgumentCaptor.forClass(ActivityAssignedPayload.class);
        verify(eventPublisher).activityAssigned(payload.capture());
        assertEquals(new ActivityAssignedPayload(ASSIGNMENT_ID, CHILD_ID, PARENT_ID, ACTIVITY_ID, "Passwords", due), payload.getValue());
    }

    @Test
    void assignPublishesOnlyAfterTheTransactionCommits() {
        parentOwnsChild();
        when(activityRepository.findByIdAndIsActiveTrue(ACTIVITY_ID)).thenReturn(Optional.of(activity));
        saveReturnsTheAssignment();
        TransactionSynchronizationManager.initSynchronization();

        service.assign(tokenFor(PARENT_ID), new AssignmentRequest(CHILD_ID, ACTIVITY_ID, null));

        verify(eventPublisher, never()).activityAssigned(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(eventPublisher).activityAssigned(any());
    }

    @Test
    void assignDoesNotPublishWhenTheTransactionRollsBack() {
        parentOwnsChild();
        when(activityRepository.findByIdAndIsActiveTrue(ACTIVITY_ID)).thenReturn(Optional.of(activity));
        saveReturnsTheAssignment();
        TransactionSynchronizationManager.initSynchronization();

        service.assign(tokenFor(PARENT_ID), new AssignmentRequest(CHILD_ID, ACTIVITY_ID, null));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(eventPublisher, never()).activityAssigned(any());
    }

    @Test
    void aFailingPublisherDoesNotFailTheAssignment() {
        parentOwnsChild();
        when(activityRepository.findByIdAndIsActiveTrue(ACTIVITY_ID)).thenReturn(Optional.of(activity));
        saveReturnsTheAssignment();
        when(eventPublisher.activityAssigned(any())).thenThrow(new IllegalStateException("broker down"));

        var response = service.assign(tokenFor(PARENT_ID), new AssignmentRequest(CHILD_ID, ACTIVITY_ID, null));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void assignRefusesAnUnknownCaller() {
        var response = service.assign("garbage", new AssignmentRequest(CHILD_ID, ACTIVITY_ID, null));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE, response.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
    }

    @Test
    void assignAnswers403ToANonParentAnd404ForAnotherFamilysChild() {
        var other = UUID.randomUUID();
        when(accessPolicy.manageChild(other, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.FORBIDDEN, null));
        when(accessPolicy.manageChild(PARENT_ID, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.NOT_VISIBLE, null));

        var notParent = service.assign(tokenFor(other), new AssignmentRequest(CHILD_ID, ACTIVITY_ID, null));
        var notYours = service.assign(tokenFor(PARENT_ID), new AssignmentRequest(CHILD_ID, ACTIVITY_ID, null));

        assertEquals(HttpStatus.FORBIDDEN, notParent.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, notYours.getStatusCode());
        verify(assignmentRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).activityAssigned(any());
    }

    @Test
    void assignRejectsADueDateInThePast() {
        parentOwnsChild();

        var past = service.assign(tokenFor(PARENT_ID), new AssignmentRequest(CHILD_ID, ACTIVITY_ID, NOW.minusSeconds(1)));
        var now = service.assign(tokenFor(PARENT_ID), new AssignmentRequest(CHILD_ID, ACTIVITY_ID, NOW));

        assertEquals(HttpStatus.BAD_REQUEST, past.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, now.getStatusCode());
        assertEquals(SmartKinAppConstants.DUE_DATE_IN_PAST_MESSAGE, past.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
    }

    @Test
    void assignAnswers404ForAMissingOrInactiveActivity() {
        parentOwnsChild();
        when(activityRepository.findByIdAndIsActiveTrue(ACTIVITY_ID)).thenReturn(Optional.empty());

        var response = service.assign(tokenFor(PARENT_ID), new AssignmentRequest(CHILD_ID, ACTIVITY_ID, null));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(SmartKinAppConstants.ACTIVITY_NOT_FOUND_MESSAGE, response.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
    }

    @Test
    void assignAnswers409WhenTheChildAlreadyHasItOpen() {
        parentOwnsChild();
        when(activityRepository.findByIdAndIsActiveTrue(ACTIVITY_ID)).thenReturn(Optional.of(activity));
        when(assignmentRepository.existsOpenAssignment(CHILD_ID, ACTIVITY_ID)).thenReturn(true);

        var response = service.assign(tokenFor(PARENT_ID), new AssignmentRequest(CHILD_ID, ACTIVITY_ID, null));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(assignmentRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).activityAssigned(any());
    }

    // list

    @Test
    void aParentListsTheAssignmentsOfAllTheirChildrenWithProgress() {
        var assignment = assignment();
        var progress = new ActivityProgressEntity();
        progress.setAssignment(assignment);
        progress.setCompletionPercentage(40);
        when(accessPolicy.isParent(PARENT_ID)).thenReturn(true);
        when(assignmentRepository.findByChildParentId(any(UUID.class), any())).thenReturn(new PageImpl<>(List.of(assignment), PageRequest.of(0, 20), 1));
        when(progressRepository.findAllByAssignmentIdIn(anyCollection())).thenReturn(List.of(progress));

        var response = service.list(tokenFor(PARENT_ID), null, 0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var page = response.getBody();
        assertEquals(1, page.totalElements());
        assertEquals(1, page.totalPages());
        assertEquals(20, page.size());
        assertEquals(40, page.content().get(0).progress().completionPercentage());
        assertEquals(ASSIGNMENT_ID, page.content().get(0).progress().assignmentId());
    }

    @Test
    void anAssignmentWithoutProgressHasNullProgress() {
        when(accessPolicy.isParent(PARENT_ID)).thenReturn(true);
        when(assignmentRepository.findByChildParentId(any(UUID.class), any())).thenReturn(new PageImpl<>(List.of(assignment())));
        when(progressRepository.findAllByAssignmentIdIn(anyCollection())).thenReturn(List.of());

        var response = service.list(tokenFor(PARENT_ID), null, 0, 20);

        assertNull(response.getBody().content().get(0).progress());
    }

    @Test
    void aParentCanFilterByOneOfTheirChildren() {
        when(accessPolicy.viewChild(PARENT_ID, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.OWNER, child));
        when(assignmentRepository.findByChildId(any(UUID.class), any())).thenReturn(new PageImpl<>(List.of()));
        when(progressRepository.findAllByAssignmentIdIn(anyCollection())).thenReturn(List.of());

        var response = service.list(tokenFor(PARENT_ID), CHILD_ID, 0, 5);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(assignmentRepository).findByChildId(any(UUID.class), any());
    }

    @Test
    void aChildListsTheirOwnAssignments() {
        when(accessPolicy.isParent(CHILD_ID)).thenReturn(false);
        when(accessPolicy.isChild(CHILD_ID)).thenReturn(true);
        when(assignmentRepository.findByChildId(any(UUID.class), any())).thenReturn(new PageImpl<>(List.of(assignment())));
        when(progressRepository.findAllByAssignmentIdIn(anyCollection())).thenReturn(List.of());

        var response = service.list(tokenFor(CHILD_ID), null, 0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().content().size());
    }

    @Test
    void listHidesOrRefusesWhatTheCallerCannotReach() {
        var other = UUID.randomUUID();
        when(accessPolicy.viewChild(PARENT_ID, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.NOT_VISIBLE, null));
        when(accessPolicy.viewChild(other, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.FORBIDDEN, null));
        when(accessPolicy.isParent(other)).thenReturn(false);
        when(accessPolicy.isChild(other)).thenReturn(false);

        var notYours = service.list(tokenFor(PARENT_ID), CHILD_ID, 0, 20);
        var sibling = service.list(tokenFor(other), CHILD_ID, 0, 20);
        var stranger = service.list(tokenFor(other), null, 0, 20);
        var unknown = service.list(null, null, 0, 20);

        assertEquals(HttpStatus.NOT_FOUND, notYours.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, sibling.getStatusCode());
        assertEquals(SmartKinAppConstants.ACCESS_DENIED_MESSAGE, sibling.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
        assertEquals(HttpStatus.FORBIDDEN, stranger.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, unknown.getStatusCode());
        verify(assignmentRepository, never()).findByChildId(any(), any());
    }

    @Test
    void listRejectsABadPageOrSize() {
        for (var bad : List.of(new int[]{-1, 20}, new int[]{0, 0}, new int[]{0, 101})) {
            var response = service.list(tokenFor(PARENT_ID), null, bad[0], bad[1]);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals(SmartKinAppConstants.INVALID_PAGE_MESSAGE, response.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
        }
        assertTrue(service.list(tokenFor(PARENT_ID), null, 0, 100).getStatusCode() != HttpStatus.BAD_REQUEST);
    }

    // get

    @Test
    void getReturnsTheAssignmentWithItsProgress() {
        var assignment = assignment();
        var progress = new ActivityProgressEntity();
        progress.setAssignment(assignment);
        progress.setCompletionPercentage(100);
        progress.setScore(80);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(accessPolicy.viewChild(PARENT_ID, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.OWNER, child));
        when(progressRepository.findByAssignmentId(ASSIGNMENT_ID)).thenReturn(Optional.of(progress));

        var response = service.get(tokenFor(PARENT_ID), ASSIGNMENT_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(80, response.getBody().progress().score());
    }

    @Test
    void getReturnsNullProgressBeforeTheChildStarts() {
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment()));
        when(accessPolicy.viewChild(CHILD_ID, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.SELF, child));
        when(progressRepository.findByAssignmentId(ASSIGNMENT_ID)).thenReturn(Optional.empty());

        var response = service.get(tokenFor(CHILD_ID), ASSIGNMENT_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody().progress());
    }

    @Test
    void getHidesMissingAndForeignAssignmentsBehind404() {
        var other = UUID.randomUUID();
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment()));
        when(accessPolicy.viewChild(other, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.NOT_VISIBLE, null));
        var foreign = service.get(tokenFor(other), ASSIGNMENT_ID);

        var missingId = UUID.randomUUID();
        when(assignmentRepository.findById(missingId)).thenReturn(Optional.empty());
        var missing = service.get(tokenFor(PARENT_ID), missingId);
        var unknown = service.get("garbage", ASSIGNMENT_ID);

        assertEquals(HttpStatus.NOT_FOUND, foreign.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals(SmartKinAppConstants.ASSIGNMENT_NOT_FOUND_MESSAGE, missing.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
        assertEquals(HttpStatus.FORBIDDEN, unknown.getStatusCode());
    }
}
