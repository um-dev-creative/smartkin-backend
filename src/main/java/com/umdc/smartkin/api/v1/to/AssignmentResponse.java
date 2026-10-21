package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/// An activity assigned to a child.
///
/// @param assignmentId  the assignment's id
/// @param childId       the child
/// @param activityId    the activity
/// @param activityTitle title of the activity
/// @param lessonId      lesson the activity plays, `null` when it has none
/// @param assignedBy    the parent who assigned it
/// @param assignedAt    when it was assigned
/// @param dueDate       deadline, `null` when there is none
/// @param progress      progress so far, `null` before the child starts
@Schema(name = "Assignment")
public record AssignmentResponse(UUID assignmentId, UUID childId, UUID activityId, String activityTitle, UUID lessonId, UUID assignedBy,
                                 Instant assignedAt, Instant dueDate, ProgressResponse progress) {

    /// Returns a copy that carries the given progress.
    ///
    /// @param value the progress, may be `null`
    /// @return the copy
    public AssignmentResponse withProgress(ProgressResponse value) {
        return new AssignmentResponse(assignmentId, childId, activityId, activityTitle, lessonId, assignedBy, assignedAt, dueDate, value);
    }
}
