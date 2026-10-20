package com.umdc.smartkin.catalog;

import com.umdc.smartkin.mongo.document.LessonDocument;

import java.util.UUID;

/// What the lesson catalog shows: enough to list and open a lesson, without its scenes or quiz.
public record LessonSummary(UUID id, String slug, UUID topicId, String title, String description, String locale, int version) {

    public static LessonSummary from(LessonDocument lesson) {
        return new LessonSummary(lesson.id(), lesson.slug(), lesson.topicId(), lesson.title(), lesson.description(),
                lesson.locale(), lesson.version());
    }
}
