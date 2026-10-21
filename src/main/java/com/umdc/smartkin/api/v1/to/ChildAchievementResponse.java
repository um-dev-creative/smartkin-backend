package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/// An achievement a child has earned.
///
/// @param childAchievementId id of the award
/// @param achievementId      the achievement
/// @param name               its name
/// @param description        what it recognizes
/// @param icon               icon reference
/// @param pointsValue        points the achievement is worth
/// @param earnedAt           when the child earned it
@Schema(name = "ChildAchievement")
public record ChildAchievementResponse(UUID childAchievementId, UUID achievementId, String name, String description, String icon,
                                       int pointsValue, Instant earnedAt) {
}
