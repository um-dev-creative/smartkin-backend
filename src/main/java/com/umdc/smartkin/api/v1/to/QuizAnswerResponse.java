package com.umdc.smartkin.api.v1.to;

import java.util.List;

/// How one question was graded.
///
/// @param questionId        the question
/// @param selectedOptionIds what the child picked
/// @param correct           the selection equals the correct options exactly
/// @param pointsAwarded     points earned for the question
/// @param feedback          explanation written in the lesson, `null` when there is none
public record QuizAnswerResponse(String questionId, List<String> selectedOptionIds, boolean correct, int pointsAwarded, String feedback) {
}
