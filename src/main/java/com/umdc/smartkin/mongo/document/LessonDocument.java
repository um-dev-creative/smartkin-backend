package com.umdc.smartkin.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// An interactive lesson: the animated scenes the young user plays and the quiz that follows.
/// Stored in the `lessons` collection; `topicId` points at `smartkin.topics` in PostgreSQL.
/// Assets (images, Lottie files) are Supabase Storage paths, never inline binaries.
@Document(collection = "lessons")
@CompoundIndex(name = "topic_status", def = "{'topicId': 1, 'status': 1}")
public record LessonDocument(
        @Id UUID id,
        @Indexed(unique = true) String slug,
        UUID topicId,
        String title,
        String description,
        String locale,
        LessonStatus status,
        int version,
        List<Scene> scenes,
        Quiz quiz,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt) {

    /// One animated step. `definition` holds the scene script the Angular player reads (elements, timeline).
    public record Scene(String id, int order, String title, String narration, long durationMs,
                        List<Interaction> interactions, Map<String, Object> definition) {
    }

    /// A point of a scene where the user acts (drag, choose, order, tap). `config` is specific to `type`.
    public record Interaction(String id, String type, String prompt, Map<String, Object> config) {
    }

    /// The evaluation tied to the lesson. `passingPercentage` and `maxAttempts` are configurable per lesson.
    public record Quiz(int passingPercentage, int maxAttempts, List<Question> questions) {
    }

    public record Question(String id, String sceneId, String type, String prompt, List<Option> options,
                           int points, String feedback) {
    }

    public record Option(String id, String text, boolean correct) {
    }
}
