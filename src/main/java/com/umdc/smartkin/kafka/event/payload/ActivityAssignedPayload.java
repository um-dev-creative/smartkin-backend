package com.umdc.smartkin.kafka.event.payload;

import java.time.Instant;
import java.util.UUID;

/// A parent assigned an activity to a child (`smartkin.assigned_activities`).
public record ActivityAssignedPayload(UUID assignmentId, UUID childId, UUID parentId, UUID activityId, String activityTitle,
                                      Instant dueDate) {
}
