package com.umdc.smartkin.api.v1.to;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/// Body of `POST /assignments`.
///
/// @param childId    the child who gets the activity
/// @param activityId the activity to assign
/// @param dueDate    optional deadline; it must be in the future
public record AssignmentRequest(@NotNull UUID childId, @NotNull UUID activityId, Instant dueDate) {
}
