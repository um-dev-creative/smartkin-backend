package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ActivityResponse;
import com.umdc.smartkin.api.v1.to.LessonResponse;
import com.umdc.smartkin.api.v1.to.TopicResponse;
import com.umdc.smartkin.catalog.LessonSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

/// Read-only catalog: topics, published lessons and active activities. Any authenticated user may read it.
public interface CatalogService {

    /// Lists the topics.
    ///
    /// @return 200 with the topics ordered by name
    default ResponseEntity<List<TopicResponse>> topics() {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Lists the published lessons of a topic.
    ///
    /// @param topicId the topic's id
    /// @return 200 with the lessons; 404 when the topic does not exist
    default ResponseEntity<List<LessonSummary>> topicLessons(UUID topicId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Returns a published lesson.
    ///
    /// @param lessonId the lesson's id
    /// @return 200 with the lesson; 404 when it does not exist or is not published
    default ResponseEntity<LessonResponse> lesson(UUID lessonId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Lists the active activities.
    ///
    /// @param topicId restrict to one topic, or `null` for all
    /// @return 200 with the activities
    default ResponseEntity<List<ActivityResponse>> activities(UUID topicId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Returns an active activity.
    ///
    /// @param activityId the activity's id
    /// @return 200 with the activity; 404 when it does not exist or is not active
    default ResponseEntity<ActivityResponse> activity(UUID activityId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
