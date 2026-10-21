package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.CatalogService;
import com.umdc.smartkin.api.v1.to.ActivityResponse;
import com.umdc.smartkin.api.v1.to.LessonResponse;
import com.umdc.smartkin.api.v1.to.TopicResponse;
import com.umdc.smartkin.catalog.LessonSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/// REST controller for the catalog.
@RestController
@RequestMapping("/api/v1")
public class CatalogController implements CatalogApi {

    private final CatalogService catalogService;

    /// Creates the controller.
    ///
    /// @param catalogService the service that backs the operations
    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Override
    public CatalogService getService() {
        return catalogService;
    }

    @Override
    public ResponseEntity<List<TopicResponse>> listTopics() {
        return catalogService.topics();
    }

    @Override
    public ResponseEntity<List<LessonSummary>> listTopicLessons(UUID topicId) {
        return catalogService.topicLessons(topicId);
    }

    @Override
    public ResponseEntity<LessonResponse> getLesson(UUID lessonId) {
        return catalogService.lesson(lessonId);
    }

    @Override
    public ResponseEntity<List<ActivityResponse>> listActivities(UUID topicId) {
        return catalogService.activities(topicId);
    }

    @Override
    public ResponseEntity<ActivityResponse> getActivity(UUID activityId) {
        return catalogService.activity(activityId);
    }
}
