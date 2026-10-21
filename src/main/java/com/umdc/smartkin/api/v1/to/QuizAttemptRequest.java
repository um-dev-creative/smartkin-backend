package com.umdc.smartkin.api.v1.to;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/// Body of `POST /assignments/{id}/quiz-attempts`.
///
/// @param answers the answers; questions left out count as unanswered
public record QuizAttemptRequest(@NotNull @Valid List<QuizAnswerRequest> answers) {
}
