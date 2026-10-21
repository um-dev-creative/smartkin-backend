package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.QuizAnswerRequest;
import com.umdc.smartkin.api.v1.to.QuizAttemptRequest;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import com.umdc.smartkin.jpa.domain.ActivityEntity;
import com.umdc.smartkin.jpa.domain.AssignedActivityEntity;
import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.repository.AssignedActivityRepository;
import com.umdc.smartkin.kafka.event.DomainEventDispatcher;
import com.umdc.smartkin.kafka.event.payload.QuizCompletedPayload;
import com.umdc.smartkin.mapper.QuizAttemptMapper;
import com.umdc.smartkin.mongo.document.LessonDocument;
import com.umdc.smartkin.mongo.document.LessonStatus;
import com.umdc.smartkin.mongo.document.QuizAttemptDocument;
import com.umdc.smartkin.mongo.repository.LessonRepository;
import com.umdc.smartkin.mongo.repository.QuizAttemptRepository;
import com.umdc.smartkin.quiz.QuizGrader;
import com.umdc.smartkin.security.access.AccessPolicy;
import com.umdc.smartkin.security.access.ChildAccess;
import com.umdc.smartkin.security.access.ChildAccessDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.umdc.smartkin.support.TestData.CHILD_ID;
import static com.umdc.smartkin.support.TestData.LESSON_ID;
import static com.umdc.smartkin.support.TestData.PARENT_ID;
import static com.umdc.smartkin.support.TestData.lesson;
import static com.umdc.smartkin.support.TestData.tokenFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizAttemptServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private AccessPolicy accessPolicy;
    @Mock
    private AssignedActivityRepository assignmentRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private QuizAttemptRepository attemptRepository;
    @Mock
    private ProgressRecorder progressRecorder;
    @Mock
    private DomainEventDispatcher events;
    private QuizAttemptServiceImpl service;
    private AssignedActivityEntity assignment;

    @BeforeEach
    void setUp() {
        service = new QuizAttemptServiceImpl(accessPolicy, assignmentRepository, lessonRepository, attemptRepository, new QuizGrader(),
                progressRecorder, events, new QuizAttemptMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        var child = new ChildEntity();
        child.setId(CHILD_ID);
        var activity = new ActivityEntity();
        activity.setLessonId(LESSON_ID);
        assignment = new AssignedActivityEntity();
        assignment.setId(ASSIGNMENT_ID);
        assignment.setChild(child);
        assignment.setActivity(activity);
    }

    private static QuizAttemptRequest correctAnswers() {
        return new QuizAttemptRequest(List.of(new QuizAnswerRequest("q1", List.of("o2"))));
    }

    private static QuizAttemptRequest wrongAnswers() {
        return new QuizAttemptRequest(List.of(new QuizAnswerRequest("q1", List.of("o1"))));
    }

    private void childSubmits() {
        when(assignmentRepository.findForUpdateById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(accessPolicy.viewChild(CHILD_ID, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.SELF, assignment.getChild()));
    }

    private void lessonIsPublished() {
        when(lessonRepository.findById(LESSON_ID)).thenReturn(Optional.of(lesson(LessonStatus.PUBLISHED)));
    }

    @Test
    void aPassedAttemptIsGradedStoredRecordedAndPublished() {
        childSubmits();
        lessonIsPublished();
        when(attemptRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(0L);
        when(attemptRepository.countByChildIdAndLessonId(CHILD_ID, LESSON_ID)).thenReturn(2L);

        var response = service.submit(tokenFor(CHILD_ID), ASSIGNMENT_ID, correctAnswers());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        var body = response.getBody();
        assertEquals(10, body.score());
        assertEquals(10, body.maxScore());
        assertEquals(100, body.percentage());
        assertEquals(true, body.passed());
        assertEquals(3, body.attemptNumber());
        assertEquals(2, body.lessonVersion());
        assertEquals(NOW, body.completedAt());
        assertEquals("The right answer is to tell an adult", body.answers().get(0).feedback());
        var stored = ArgumentCaptor.forClass(QuizAttemptDocument.class);
        verify(attemptRepository).save(stored.capture());
        assertEquals(ASSIGNMENT_ID, stored.getValue().assignmentId());
        assertEquals(CHILD_ID, stored.getValue().childId());
        verify(progressRecorder).record(assignment, 100, true, NOW);
        var event = ArgumentCaptor.forClass(QuizCompletedPayload.class);
        verify(events).quizCompleted(event.capture());
        assertEquals(new QuizCompletedPayload(stored.getValue().id(), CHILD_ID, LESSON_ID, ASSIGNMENT_ID, 3, 10, 10, 100, true, NOW), event.getValue());
    }

    @Test
    void aFailedAttemptIsStoredAndPublishedWithoutPassing() {
        childSubmits();
        lessonIsPublished();
        when(attemptRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(1L);
        when(attemptRepository.countByChildIdAndLessonId(CHILD_ID, LESSON_ID)).thenReturn(1L);

        var response = service.submit(tokenFor(CHILD_ID), ASSIGNMENT_ID, wrongAnswers());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(0, response.getBody().score());
        assertEquals(false, response.getBody().passed());
        assertEquals(2, response.getBody().attemptNumber());
        verify(progressRecorder).record(assignment, 0, false, NOW);
        verify(events).quizCompleted(any());
    }

    @Test
    void theAttemptLimitAnswers409() {
        childSubmits();
        lessonIsPublished();
        when(attemptRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(3L);

        var response = service.submit(tokenFor(CHILD_ID), ASSIGNMENT_ID, correctAnswers());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(SmartKinAppConstants.MAX_ATTEMPTS_REACHED_MESSAGE, response.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
        verify(attemptRepository, never()).save(any());
        verify(events, never()).quizCompleted(any());
    }

    @Test
    void answersThatDoNotMatchTheQuizAnswer400AndWriteNothing() {
        childSubmits();
        lessonIsPublished();
        when(attemptRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(0L);

        var response = service.submit(tokenFor(CHILD_ID), ASSIGNMENT_ID,
                new QuizAttemptRequest(List.of(new QuizAnswerRequest("unknown", List.of("x")))));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER).contains("unknown"));
        verify(attemptRepository, never()).save(any());
        verify(progressRecorder, never()).record(any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    void aDuplicateAttemptNumberAnswers409BeforeTouchingTheProgress() {
        childSubmits();
        lessonIsPublished();
        when(attemptRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(0L);
        when(attemptRepository.countByChildIdAndLessonId(CHILD_ID, LESSON_ID)).thenReturn(0L);
        when(attemptRepository.save(any(QuizAttemptDocument.class))).thenThrow(new DuplicateKeyException("dup"));

        var response = service.submit(tokenFor(CHILD_ID), ASSIGNMENT_ID, correctAnswers());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(SmartKinAppConstants.ATTEMPT_CONFLICT_MESSAGE, response.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
        verify(progressRecorder, never()).record(any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean(), any());
        verify(events, never()).quizCompleted(any());
    }

    @Test
    void ifTheProgressCannotBeWrittenTheAttemptIsDeletedAndTheErrorPropagates() {
        childSubmits();
        lessonIsPublished();
        when(attemptRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(0L);
        when(attemptRepository.countByChildIdAndLessonId(CHILD_ID, LESSON_ID)).thenReturn(0L);
        doThrow(new IllegalStateException("db down")).when(progressRecorder).record(any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(), any());

        assertThrows(IllegalStateException.class, () -> service.submit(tokenFor(CHILD_ID), ASSIGNMENT_ID, correctAnswers()));

        var stored = ArgumentCaptor.forClass(QuizAttemptDocument.class);
        verify(attemptRepository).save(stored.capture());
        verify(attemptRepository).deleteById(stored.getValue().id());
        verify(events, never()).quizCompleted(any());
    }

    @Test
    void thereIsNoQuizWhenTheActivityHasNoLessonOrTheLessonIsNotPublishedOrHasNoQuiz() {
        childSubmits();
        var noLesson = new ActivityEntity();
        assignment.setActivity(noLesson);
        var withoutLessonId = service.submit(tokenFor(CHILD_ID), ASSIGNMENT_ID, correctAnswers());

        var activity = new ActivityEntity();
        activity.setLessonId(LESSON_ID);
        assignment.setActivity(activity);
        when(lessonRepository.findById(LESSON_ID)).thenReturn(Optional.of(lesson(LessonStatus.DRAFT)));
        var draft = service.submit(tokenFor(CHILD_ID), ASSIGNMENT_ID, correctAnswers());

        var published = lesson(LessonStatus.PUBLISHED);
        var withoutQuiz = new LessonDocument(published.id(), published.slug(), published.topicId(), published.title(), published.description(),
                published.locale(), published.status(), published.version(), published.scenes(), null, published.createdBy(),
                published.createdAt(), published.updatedAt());
        when(lessonRepository.findById(LESSON_ID)).thenReturn(Optional.of(withoutQuiz));
        var noQuiz = service.submit(tokenFor(CHILD_ID), ASSIGNMENT_ID, correctAnswers());

        var emptyQuiz = new LessonDocument(published.id(), published.slug(), published.topicId(), published.title(), published.description(),
                published.locale(), published.status(), published.version(), published.scenes(),
                new LessonDocument.Quiz(70, 3, List.of()), published.createdBy(), published.createdAt(), published.updatedAt());
        when(lessonRepository.findById(LESSON_ID)).thenReturn(Optional.of(emptyQuiz));
        var noQuestions = service.submit(tokenFor(CHILD_ID), ASSIGNMENT_ID, correctAnswers());

        for (var response : List.of(withoutLessonId, draft, noQuiz, noQuestions)) {
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals(SmartKinAppConstants.QUIZ_NOT_AVAILABLE_MESSAGE, response.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
        }
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void onlyTheAssignedChildSubmits() {
        var other = UUID.randomUUID();
        when(assignmentRepository.findForUpdateById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(accessPolicy.viewChild(PARENT_ID, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.OWNER, assignment.getChild()));
        when(accessPolicy.viewChild(other, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.NOT_VISIBLE, null));
        var missingId = UUID.randomUUID();
        when(assignmentRepository.findForUpdateById(missingId)).thenReturn(Optional.empty());

        var parent = service.submit(tokenFor(PARENT_ID), ASSIGNMENT_ID, correctAnswers());
        var foreign = service.submit(tokenFor(other), ASSIGNMENT_ID, correctAnswers());
        var missing = service.submit(tokenFor(CHILD_ID), missingId, correctAnswers());
        var unknown = service.submit("garbage", ASSIGNMENT_ID, correctAnswers());

        assertEquals(HttpStatus.FORBIDDEN, parent.getStatusCode());
        assertEquals(SmartKinAppConstants.ONLY_THE_CHILD_MESSAGE, parent.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
        assertEquals(HttpStatus.NOT_FOUND, foreign.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, unknown.getStatusCode());
        verify(attemptRepository, never()).save(any());
    }

    // list

    private QuizAttemptDocument storedAttempt() {
        return new QuizAttemptDocument(UUID.randomUUID(), CHILD_ID, LESSON_ID, 2, ASSIGNMENT_ID, 1,
                List.of(new QuizAttemptDocument.Answer("q1", List.of("o2"), true, 10, NOW)), 10, 10, 100, true, NOW, NOW);
    }

    @Test
    void aParentAndTheChildListTheAttemptsWithFeedback() {
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(accessPolicy.viewChild(PARENT_ID, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.OWNER, assignment.getChild()));
        lessonIsPublished();
        when(attemptRepository.findByAssignmentIdOrderByAttemptNumber(ASSIGNMENT_ID)).thenReturn(List.of(storedAttempt()));

        var response = service.list(tokenFor(PARENT_ID), ASSIGNMENT_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("The right answer is to tell an adult", response.getBody().get(0).answers().get(0).feedback());
    }

    @Test
    void listWorksWhenTheActivityHasNoLessonAnymore() {
        assignment.setActivity(new ActivityEntity());
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(accessPolicy.viewChild(CHILD_ID, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.SELF, assignment.getChild()));
        when(attemptRepository.findByAssignmentIdOrderByAttemptNumber(ASSIGNMENT_ID)).thenReturn(List.of(storedAttempt()));

        var response = service.list(tokenFor(CHILD_ID), ASSIGNMENT_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(null, response.getBody().get(0).answers().get(0).feedback());
        verify(lessonRepository, never()).findById(any());
    }

    @Test
    void listHidesMissingAndForeignAssignments() {
        var other = UUID.randomUUID();
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(accessPolicy.viewChild(other, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.NOT_VISIBLE, null));
        var missingId = UUID.randomUUID();
        when(assignmentRepository.findById(missingId)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, service.list(tokenFor(other), ASSIGNMENT_ID).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, service.list(tokenFor(PARENT_ID), missingId).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, service.list(null, ASSIGNMENT_ID).getStatusCode());
    }
}
