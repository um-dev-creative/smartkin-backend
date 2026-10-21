package com.umdc.smartkin.api.v1.to;

import jakarta.validation.constraints.NotBlank;

/// Body of `PUT /assignments/{id}/feedback`.
///
/// @param parentFeedback what the parent wants to tell the child
public record FeedbackRequest(@NotBlank String parentFeedback) {
}
