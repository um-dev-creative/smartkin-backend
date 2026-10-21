package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.catalog.LessonCatalogService;
import com.umdc.smartkin.catalog.LessonSummary;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import com.umdc.smartkin.jpa.domain.ActivityEntity;
import com.umdc.smartkin.jpa.domain.TopicEntity;
import com.umdc.smartkin.jpa.repository.ActivityRepository;
import com.umdc.smartkin.jpa.repository.TopicRepository;
import com.umdc.smartkin.mapper.CatalogMapperImpl;
import com.umdc.smartkin.mongo.document.LessonStatus;
import com.umdc.smartkin.mongo.repository.LessonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static com.umdc.smartkin.support.TestData.ACTIVITY_ID;
import static com.umdc.smartkin.support.TestData.LESSON_ID;
import static com.umdc.smartkin.support.TestData.TOPIC_ID;
import static com.umdc.smartkin.support.TestData.lesson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceImplTest {

    @Mock
    private TopicRepository topicRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private LessonCatalogService lessonCatalog;
    private CatalogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CatalogServiceImpl(topicRepository, activityRepository, lessonRepository, lessonCatalog, new CatalogMapperImpl());
    }

    private static TopicEntity topic() {
        var topic = new TopicEntity();
        topic.setId(TOPIC_ID);
        topic.setName("Screen time");
        return topic;
    }

    private static ActivityEntity activity() {
        var activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID);
        activity.setTopic(topic());
        activity.setTitle("Passwords");
        activity.setLessonId(LESSON_ID);
        activity.setPointsReward(50);
        activity.setDurationMinutes(10);
        return activity;
    }

    @Test
    void topicsAreListedByName() {
        when(topicRepository.findAllByOrderByName()).thenReturn(List.of(topic()));

        var response = service.topics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(TOPIC_ID, response.getBody().get(0).topicId());
    }

    @Test
    void topicLessonsComeFromTheCachedCatalog() {
        var summary = new LessonSummary(LESSON_ID, "passwords", TOPIC_ID, "Passwords", "d", "en", 2);
        when(topicRepository.existsById(TOPIC_ID)).thenReturn(true);
        when(lessonCatalog.publishedLessons(TOPIC_ID)).thenReturn(List.of(summary));

        var response = service.topicLessons(TOPIC_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(summary), response.getBody());
    }

    @Test
    void topicLessonsOfAnUnknownTopicAre404() {
        when(topicRepository.existsById(TOPIC_ID)).thenReturn(false);

        var response = service.topicLessons(TOPIC_ID);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(SmartKinAppConstants.TOPIC_NOT_FOUND_MESSAGE, response.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
        verify(lessonCatalog, never()).publishedLessons(TOPIC_ID);
    }

    @Test
    void aPublishedLessonIsServed() {
        when(lessonRepository.findById(LESSON_ID)).thenReturn(Optional.of(lesson(LessonStatus.PUBLISHED)));

        var response = service.lesson(LESSON_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(LESSON_ID, response.getBody().id());
        assertEquals("Passwords", response.getBody().title());
    }

    @Test
    void aDraftAnArchivedOrMissingLessonIs404() {
        when(lessonRepository.findById(LESSON_ID)).thenReturn(Optional.of(lesson(LessonStatus.DRAFT)));
        var draft = service.lesson(LESSON_ID);

        when(lessonRepository.findById(LESSON_ID)).thenReturn(Optional.of(lesson(LessonStatus.ARCHIVED)));
        var archived = service.lesson(LESSON_ID);

        when(lessonRepository.findById(LESSON_ID)).thenReturn(Optional.empty());
        var missing = service.lesson(LESSON_ID);

        for (var response : List.of(draft, archived, missing)) {
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals(SmartKinAppConstants.LESSON_NOT_FOUND_MESSAGE, response.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
            assertNull(response.getBody());
        }
    }

    @Test
    void activitiesAreListedForAllTopicsOrOne() {
        when(activityRepository.findAllByIsActiveTrueOrderByTitle()).thenReturn(List.of(activity()));
        when(activityRepository.findAllByTopicIdAndIsActiveTrueOrderByTitle(TOPIC_ID)).thenReturn(List.of());

        var all = service.activities(null);
        var ofTopic = service.activities(TOPIC_ID);

        assertEquals(1, all.getBody().size());
        assertEquals(LESSON_ID, all.getBody().get(0).lessonId());
        assertEquals(0, ofTopic.getBody().size());
        verify(activityRepository, never()).findAllByTopicIdAndIsActiveTrueOrderByTitle(null);
    }

    @Test
    void anActiveActivityIsServedAndAnInactiveOrMissingOneIs404() {
        when(activityRepository.findByIdAndIsActiveTrue(ACTIVITY_ID)).thenReturn(Optional.of(activity()));
        var found = service.activity(ACTIVITY_ID);

        when(activityRepository.findByIdAndIsActiveTrue(ACTIVITY_ID)).thenReturn(Optional.empty());
        var missing = service.activity(ACTIVITY_ID);

        assertEquals(HttpStatus.OK, found.getStatusCode());
        assertEquals(ACTIVITY_ID, found.getBody().activityId());
        assertEquals(TOPIC_ID, found.getBody().topicId());
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals(SmartKinAppConstants.ACTIVITY_NOT_FOUND_MESSAGE, missing.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
    }
}
