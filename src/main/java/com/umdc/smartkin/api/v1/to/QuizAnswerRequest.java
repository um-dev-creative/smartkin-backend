package com.umdc.smartkin.api.v1.to;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/// The options a child picked for one question.
///
/// @param questionId        the question
/// @param selectedOptionIds the picked options (may be empty)
public record QuizAnswerRequest(@NotBlank String questionId, @NotNull List<String> selectedOptionIds) {
}
