package com.umdc.smartkin.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/// One attempt of a child at a lesson quiz, stored in `quiz_attempts`.
/// `childId` is the backbone user id and `assignmentId` the `smartkin.assigned_activities` key; neither is a database reference.
@Document(collection = "quiz_attempts")
@CompoundIndexes({
        @CompoundIndex(name = "child_lesson_attempt", def = "{'childId': 1, 'lessonId': 1, 'attemptNumber': 1}", unique = true),
        @CompoundIndex(name = "child_completed", def = "{'childId': 1, 'completedAt': -1}")
})
public record QuizAttemptDocument(
        @Id UUID id,
        UUID childId,
        UUID lessonId,
        int lessonVersion,
        @Indexed UUID assignmentId,
        int attemptNumber,
        List<Answer> answers,
        int score,
        int maxScore,
        int percentage,
        boolean passed,
        Instant startedAt,
        Instant completedAt) {

    public record Answer(String questionId, List<String> selectedOptionIds, boolean correct, int pointsAwarded,
                         Instant answeredAt) {
    }
}
