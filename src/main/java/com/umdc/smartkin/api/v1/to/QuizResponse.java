package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/// The quiz of a lesson.
///
/// @param passingPercentage percentage needed to pass
/// @param maxAttempts       attempts allowed
/// @param questions         the questions
@Schema(name = "Quiz")
public record QuizResponse(int passingPercentage, int maxAttempts, List<QuestionResponse> questions) {
}
