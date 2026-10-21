package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ActivityResponse;
import com.umdc.smartkin.api.v1.to.LessonResponse;
import com.umdc.smartkin.api.v1.to.TopicResponse;
import com.umdc.smartkin.catalog.LessonCatalogService;
import com.umdc.smartkin.catalog.LessonSummary;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import com.umdc.smartkin.jpa.repository.ActivityRepository;
import com.umdc.smartkin.jpa.repository.TopicRepository;
import com.umdc.smartkin.mapper.CatalogMapper;
import com.umdc.smartkin.mongo.document.LessonStatus;
import com.umdc.smartkin.mongo.repository.LessonRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/// Default implementation of [CatalogService]. Topic lessons come through the Redis-cached [LessonCatalogService];
/// a lesson in detail is read from MongoDB and is only served while it is published.
@Service
public class CatalogServiceImpl implements CatalogService {

    private final TopicRepository topicRepository;
    private final ActivityRepository activityRepository;
    private final LessonRepository lessonRepository;
    private final LessonCatalogService lessonCatalog;
    private final CatalogMapper catalogMapper;

    /// Creates the service.
    ///
    /// @param topicRepository    the topic repository
    /// @param activityRepository the activity repository
    /// @param lessonRepository   the lesson repository (MongoDB)
    /// @param lessonCatalog      the cached list of published lessons per topic
    /// @param catalogMapper      the mapper to the API representation
    public CatalogServiceImpl(TopicRepository topicRepository, ActivityRepository activityRepository,
                              LessonRepository lessonRepository, LessonCatalogService lessonCatalog, CatalogMapper catalogMapper) {
        this.topicRepository = topicRepository;
        this.activityRepository = activityRepository;
        this.lessonRepository = lessonRepository;
        this.lessonCatalog = lessonCatalog;
        this.catalogMapper = catalogMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<List<TopicResponse>> topics() {
        return ResponseEntity.ok(topicRepository.findAllByOrderByName().stream().map(catalogMapper::toResponse).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<List<LessonSummary>> topicLessons(UUID topicId) {
        if (!topicRepository.existsById(topicId)) {
            return notFound(SmartKinAppConstants.TOPIC_NOT_FOUND_MESSAGE);
        }
        return ResponseEntity.ok(lessonCatalog.publishedLessons(topicId));
    }

    @Override
    public ResponseEntity<LessonResponse> lesson(UUID lessonId) {
        return lessonRepository.findById(lessonId)
                .filter(lesson -> lesson.status() == LessonStatus.PUBLISHED)
                .map(lesson -> ResponseEntity.ok(catalogMapper.toResponse(lesson)))
                .orElseGet(() -> notFound(SmartKinAppConstants.LESSON_NOT_FOUND_MESSAGE));
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<List<ActivityResponse>> activities(UUID topicId) {
        var activities = topicId == null
                ? activityRepository.findAllByIsActiveTrueOrderByTitle()
                : activityRepository.findAllByTopicIdAndIsActiveTrueOrderByTitle(topicId);
        return ResponseEntity.ok(activities.stream().map(catalogMapper::toResponse).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<ActivityResponse> activity(UUID activityId) {
        return activityRepository.findByIdAndIsActiveTrue(activityId)
                .map(activity -> ResponseEntity.ok(catalogMapper.toResponse(activity)))
                .orElseGet(() -> notFound(SmartKinAppConstants.ACTIVITY_NOT_FOUND_MESSAGE));
    }

    private static <T> ResponseEntity<T> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).header(SmartKinAppConstants.MESSAGE_ERROR_HEADER, message).build();
    }
}
