package com.umdc.smartkin.mapper;

import com.umdc.smartkin.api.v1.to.QuizAnswerResponse;
import com.umdc.smartkin.api.v1.to.QuizAttemptResponse;
import com.umdc.smartkin.mongo.document.LessonDocument;
import com.umdc.smartkin.mongo.document.QuizAttemptDocument;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Maps a stored attempt to its API representation, adding the explanation each question has in the lesson.
@Component
public class QuizAttemptMapper {

    /// Converts an attempt.
    ///
    /// @param attempt the stored attempt
    /// @param lesson  the lesson it was taken on, used only to look up the feedback of each question (may be empty)
    /// @return the API representation
    public QuizAttemptResponse toResponse(QuizAttemptDocument attempt, Optional<LessonDocument> lesson) {
        Map<String, String> feedback = lesson.map(LessonDocument::quiz).map(quiz -> quiz.questions().stream()
                .filter(question -> question.feedback() != null)
                .collect(Collectors.toMap(LessonDocument.Question::id, LessonDocument.Question::feedback, (first, second) -> first)))
                .orElse(Map.of());
        return new QuizAttemptResponse(attempt.id(), attempt.assignmentId(), attempt.lessonId(), attempt.lessonVersion(),
                attempt.attemptNumber(), attempt.score(), attempt.maxScore(), attempt.percentage(), attempt.passed(), attempt.completedAt(),
                attempt.answers().stream().map(answer -> new QuizAnswerResponse(answer.questionId(), answer.selectedOptionIds(),
                        answer.correct(), answer.pointsAwarded(), feedback.get(answer.questionId()))).toList());
    }
}
