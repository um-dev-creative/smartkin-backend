package com.umdc.smartkin.kafka.event.payload;

import java.time.Instant;
import java.util.UUID;

/// A child earned an achievement (`smartkin.child_achievements`).
public record AchievementEarnedPayload(UUID childAchievementId, UUID childId, UUID achievementId, String achievementName,
                                       int pointsValue, Instant earnedAt) {
}
