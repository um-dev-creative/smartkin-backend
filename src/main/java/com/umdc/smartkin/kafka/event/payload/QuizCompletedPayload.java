package com.umdc.smartkin.kafka.event.payload;

import java.time.Instant;
import java.util.UUID;

/// A child finished a lesson quiz (a document of the MongoDB `quiz_attempts` collection).
public record QuizCompletedPayload(UUID attemptId, UUID childId, UUID lessonId, UUID assignmentId, int attemptNumber, int score,
                                   int maxScore, int percentage, boolean passed, Instant completedAt) {
}
