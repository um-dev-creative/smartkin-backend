package com.umdc.smartkin.mongo.document;

/// Publication state of a lesson. Only `PUBLISHED` lessons reach the young user.
public enum LessonStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
