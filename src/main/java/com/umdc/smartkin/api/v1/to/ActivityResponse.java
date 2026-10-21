package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/// An activity a parent can assign.
///
/// @param activityId      the activity's id
/// @param topicId         the topic it belongs to
/// @param lessonId        the lesson (MongoDB) it plays and grades, `null` when it has none
/// @param title           the title
/// @param description     what the activity is about
/// @param durationMinutes expected duration
/// @param pointsReward    points the child earns by completing it
@Schema(name = "Activity")
public record ActivityResponse(UUID activityId, UUID topicId, UUID lessonId, String title, String description,
                               int durationMinutes, int pointsReward) {
}
