package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/// Progress of a child on an assignment.
///
/// @param assignmentId         the assignment
/// @param completionPercentage 0 to 100
/// @param startTime            when the child started
/// @param lastAccessed         last time the child reported progress
/// @param completionTime       when the child finished, `null` while open
/// @param score                quiz score, `null` until graded
/// @param parentFeedback       what the parent wrote, `null` if nothing
@Schema(name = "Progress")
public record ProgressResponse(UUID assignmentId, int completionPercentage, Instant startTime, Instant lastAccessed,
                               Instant completionTime, Integer score, String parentFeedback) {
}
