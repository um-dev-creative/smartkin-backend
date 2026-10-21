package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.QuizAttemptRequest;
import com.umdc.smartkin.api.v1.to.QuizAttemptResponse;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import com.umdc.smartkin.jpa.domain.AssignedActivityEntity;
import com.umdc.smartkin.jpa.repository.AssignedActivityRepository;
import com.umdc.smartkin.kafka.event.DomainEventDispatcher;
import com.umdc.smartkin.kafka.event.payload.QuizCompletedPayload;
import com.umdc.smartkin.mapper.QuizAttemptMapper;
import com.umdc.smartkin.mongo.document.LessonDocument;
import com.umdc.smartkin.mongo.document.LessonStatus;
import com.umdc.smartkin.mongo.document.QuizAttemptDocument;
import com.umdc.smartkin.mongo.repository.LessonRepository;
import com.umdc.smartkin.mongo.repository.QuizAttemptRepository;
import com.umdc.smartkin.quiz.InvalidQuizAnswersException;
import com.umdc.smartkin.quiz.QuizGrader;
import com.umdc.smartkin.security.access.AccessPolicy;
import com.umdc.smartkin.security.access.ChildAccessDecision;
import com.umdc.smartkin.util.JwtUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Default implementation of [QuizAttemptService]. The assignment is read with a write lock, so attempts of the same assignment are graded
/// one at a time and the attempt limit cannot be passed by concurrent submissions. The attempt is inserted in MongoDB first (a duplicate key
/// answers 409 before anything else was written); the progress is written next and, if that fails, the attempt is deleted again and the
/// error propagates, so the transaction rolls back and no event is sent. The event goes out after the commit.
@Service
public class QuizAttemptServiceImpl implements QuizAttemptService {

    private final AccessPolicy accessPolicy;
    private final AssignedActivityRepository assignmentRepository;
    private final LessonRepository lessonRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizGrader grader;
    private final ProgressRecorder progressRecorder;
    private final DomainEventDispatcher events;
    private final QuizAttemptMapper attemptMapper;
    private final Clock clock;

    /// Creates the service.
    ///
    /// @param accessPolicy         who may reach which child
    /// @param assignmentRepository the assignment repository
    /// @param lessonRepository     the lesson repository (MongoDB)
    /// @param attemptRepository    the attempt repository (MongoDB)
    /// @param grader               grades the answers
    /// @param progressRecorder     writes the result into the progress
    /// @param events               publishes `QUIZ_COMPLETED` after the commit
    /// @param attemptMapper        the mapper to the API representation
    /// @param clock                the current time
    public QuizAttemptServiceImpl(AccessPolicy accessPolicy, AssignedActivityRepository assignmentRepository,
                                  LessonRepository lessonRepository, QuizAttemptRepository attemptRepository, QuizGrader grader,
                                  ProgressRecorder progressRecorder, DomainEventDispatcher events, QuizAttemptMapper attemptMapper,
                                  Clock clock) {
        this.accessPolicy = accessPolicy;
        this.assignmentRepository = assignmentRepository;
        this.lessonRepository = lessonRepository;
        this.attemptRepository = attemptRepository;
        this.grader = grader;
        this.progressRecorder = progressRecorder;
        this.events = events;
        this.attemptMapper = attemptMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ResponseEntity<QuizAttemptResponse> submit(String token, UUID assignmentId, QuizAttemptRequest request) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        var found = assignmentRepository.findForUpdateById(assignmentId);
        if (found.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.ASSIGNMENT_NOT_FOUND_MESSAGE);
        }
        var assignment = found.get();
        var decision = accessPolicy.viewChild(callerId, assignment.getChild().getId()).decision();
        if (decision == ChildAccessDecision.OWNER) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.ONLY_THE_CHILD_MESSAGE);
        }
        if (decision != ChildAccessDecision.SELF) {
            return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.ASSIGNMENT_NOT_FOUND_MESSAGE);
        }
        var lesson = publishedLessonWithQuiz(assignment.getActivity().getLessonId());
        if (lesson.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.QUIZ_NOT_AVAILABLE_MESSAGE);
        }
        var quiz = lesson.get().quiz();
        if (attemptRepository.countByAssignmentId(assignmentId) >= quiz.maxAttempts()) {
            return error(HttpStatus.CONFLICT, SmartKinAppConstants.MAX_ATTEMPTS_REACHED_MESSAGE);
        }
        var now = clock.instant();
        var childId = assignment.getChild().getId();
        try {
            var graded = grader.grade(quiz, request.answers(), now);
            var attemptNumber = (int) attemptRepository.countByChildIdAndLessonId(childId, lesson.get().id()) + 1;
            var attempt = new QuizAttemptDocument(UUID.randomUUID(), childId, lesson.get().id(), lesson.get().version(), assignmentId,
                    attemptNumber, graded.answers(), graded.score(), graded.maxScore(), graded.percentage(), graded.passed(), now, now);
            attemptRepository.save(attempt);
            recordProgress(assignment, attempt);
            events.quizCompleted(new QuizCompletedPayload(attempt.id(), childId, attempt.lessonId(), assignmentId, attemptNumber,
                    graded.score(), graded.maxScore(), graded.percentage(), graded.passed(), now));
            return ResponseEntity.status(HttpStatus.CREATED).body(attemptMapper.toResponse(attempt, lesson));
        } catch (InvalidQuizAnswersException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (DuplicateKeyException e) {
            return error(HttpStatus.CONFLICT, SmartKinAppConstants.ATTEMPT_CONFLICT_MESSAGE);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<List<QuizAttemptResponse>> list(String token, UUID assignmentId) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        var found = assignmentRepository.findById(assignmentId);
        if (found.isEmpty() || !accessPolicy.viewChild(callerId, found.get().getChild().getId()).allowed()) {
            return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.ASSIGNMENT_NOT_FOUND_MESSAGE);
        }
        var lessonId = found.get().getActivity().getLessonId();
        Optional<LessonDocument> lesson = lessonId == null ? Optional.empty() : lessonRepository.findById(lessonId);
        return ResponseEntity.ok(attemptRepository.findByAssignmentIdOrderByAttemptNumber(assignmentId).stream()
                .map(attempt -> attemptMapper.toResponse(attempt, lesson)).toList());
    }

    private void recordProgress(AssignedActivityEntity assignment, QuizAttemptDocument attempt) {
        try {
            progressRecorder.record(assignment, attempt.percentage(), attempt.passed(), attempt.completedAt());
        } catch (RuntimeException e) {
            attemptRepository.deleteById(attempt.id());
            throw e;
        }
    }

    private Optional<LessonDocument> publishedLessonWithQuiz(UUID lessonId) {
        if (lessonId == null) {
            return Optional.empty();
        }
        return lessonRepository.findById(lessonId)
                .filter(lesson -> lesson.status() == LessonStatus.PUBLISHED)
                .filter(lesson -> lesson.quiz() != null && !lesson.quiz().questions().isEmpty());
    }

    private static <T> ResponseEntity<T> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).header(SmartKinAppConstants.MESSAGE_ERROR_HEADER, message).build();
    }
}
