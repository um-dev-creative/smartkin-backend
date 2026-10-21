package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/// A graded quiz attempt.
///
/// @param attemptId     the attempt's id
/// @param assignmentId  the assignment it belongs to
/// @param lessonId      the lesson
/// @param lessonVersion version of the lesson it was graded against
/// @param attemptNumber number of this attempt for the child and lesson
/// @param score         points earned
/// @param maxScore      points of the quiz
/// @param percentage    0 to 100
/// @param passed        the quiz was passed
/// @param completedAt   when it was graded
/// @param answers       how each question was graded
@Schema(name = "QuizAttempt")
public record QuizAttemptResponse(UUID attemptId, UUID assignmentId, UUID lessonId, int lessonVersion, int attemptNumber, int score,
                                  int maxScore, int percentage, boolean passed, Instant completedAt, List<QuizAnswerResponse> answers) {
}
