package com.umdc.smartkin.mapper;

import com.umdc.smartkin.mongo.document.LessonDocument;
import com.umdc.smartkin.mongo.document.LessonStatus;
import com.umdc.smartkin.mongo.document.QuizAttemptDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.umdc.smartkin.support.TestData.CHILD_ID;
import static com.umdc.smartkin.support.TestData.LESSON_ID;
import static com.umdc.smartkin.support.TestData.lesson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuizAttemptMapperTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private final QuizAttemptMapper mapper = new QuizAttemptMapper();

    private static QuizAttemptDocument attempt() {
        var answers = List.of(new QuizAttemptDocument.Answer("q1", List.of("o2"), true, 10, NOW),
                new QuizAttemptDocument.Answer("gone", List.of(), false, 0, NOW));
        return new QuizAttemptDocument(UUID.randomUUID(), CHILD_ID, LESSON_ID, 2, ASSIGNMENT_ID, 1, answers, 10, 10, 100, true, NOW, NOW);
    }

    @Test
    void mapsTheAttemptAndAddsTheFeedbackOfTheLesson() {
        var response = mapper.toResponse(attempt(), Optional.of(lesson(LessonStatus.PUBLISHED)));

        assertEquals(ASSIGNMENT_ID, response.assignmentId());
        assertEquals(LESSON_ID, response.lessonId());
        assertEquals(2, response.lessonVersion());
        assertEquals(1, response.attemptNumber());
        assertEquals(100, response.percentage());
        assertEquals(NOW, response.completedAt());
        assertEquals("The right answer is to tell an adult", response.answers().get(0).feedback());
        assertEquals(List.of("o2"), response.answers().get(0).selectedOptionIds());
        assertNull(response.answers().get(1).feedback());
    }

    @Test
    void feedbackIsNullWhenTheLessonIsGoneHasNoQuizOrNoFeedback() {
        var noQuiz = lesson(LessonStatus.PUBLISHED);
        noQuiz = new LessonDocument(noQuiz.id(), noQuiz.slug(), noQuiz.topicId(), noQuiz.title(), noQuiz.description(), noQuiz.locale(),
                noQuiz.status(), noQuiz.version(), noQuiz.scenes(), null, noQuiz.createdBy(), noQuiz.createdAt(), noQuiz.updatedAt());
        var question = new LessonDocument.Question("q1", "s1", "SINGLE_CHOICE", "?", List.of(), 10, null);
        var withoutFeedback = new LessonDocument(noQuiz.id(), noQuiz.slug(), noQuiz.topicId(), noQuiz.title(), noQuiz.description(), noQuiz.locale(),
                noQuiz.status(), noQuiz.version(), noQuiz.scenes(), new LessonDocument.Quiz(70, 3, List.of(question)), noQuiz.createdBy(),
                noQuiz.createdAt(), noQuiz.updatedAt());

        assertNull(mapper.toResponse(attempt(), Optional.empty()).answers().get(0).feedback());
        assertNull(mapper.toResponse(attempt(), Optional.of(noQuiz)).answers().get(0).feedback());
        assertNull(mapper.toResponse(attempt(), Optional.of(withoutFeedback)).answers().get(0).feedback());
    }

    @Test
    void whenALessonRepeatsAQuestionIdTheFirstFeedbackWins() {
        var published = lesson(LessonStatus.PUBLISHED);
        var first = new LessonDocument.Question("q1", "s1", "SINGLE_CHOICE", "?", List.of(), 10, "first");
        var second = new LessonDocument.Question("q1", "s1", "SINGLE_CHOICE", "?", List.of(), 10, "second");
        var duplicated = new LessonDocument(published.id(), published.slug(), published.topicId(), published.title(), published.description(),
                published.locale(), published.status(), published.version(), published.scenes(), new LessonDocument.Quiz(70, 3, List.of(first, second)),
                published.createdBy(), published.createdAt(), published.updatedAt());

        assertEquals("first", mapper.toResponse(attempt(), Optional.of(duplicated)).answers().get(0).feedback());
    }
}
