package com.umdc.smartkin.mapper;

import com.umdc.smartkin.jpa.domain.ActivityEntity;
import com.umdc.smartkin.jpa.domain.TopicEntity;
import com.umdc.smartkin.mongo.document.LessonStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static com.umdc.smartkin.support.TestData.ACTIVITY_ID;
import static com.umdc.smartkin.support.TestData.LESSON_ID;
import static com.umdc.smartkin.support.TestData.TOPIC_ID;
import static com.umdc.smartkin.support.TestData.lesson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogMapperTest {

    private final CatalogMapper mapper = new CatalogMapperImpl();

    @Test
    void mapsATopic() {
        var topic = new TopicEntity();
        topic.setId(TOPIC_ID);
        topic.setName("Screen time");
        topic.setDescription("Balance");
        topic.setIcon("clock");
        topic.setAgeRangeMin(8);
        topic.setAgeRangeMax(14);

        var response = mapper.toResponse(topic);

        assertEquals(TOPIC_ID, response.topicId());
        assertEquals("Screen time", response.name());
        assertEquals("clock", response.icon());
        assertEquals(8, response.ageRangeMin());
        assertEquals(14, response.ageRangeMax());
    }

    @Test
    void mapsAnActivityWithAndWithoutLesson() {
        var topic = new TopicEntity();
        topic.setId(TOPIC_ID);
        var activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID);
        activity.setTopic(topic);
        activity.setTitle("Passwords");
        activity.setDescription("Lesson");
        activity.setDurationMinutes(10);
        activity.setPointsReward(50);

        var without = mapper.toResponse(activity);
        activity.setLessonId(LESSON_ID);
        var with = mapper.toResponse(activity);

        assertEquals(ACTIVITY_ID, without.activityId());
        assertEquals(TOPIC_ID, without.topicId());
        assertNull(without.lessonId());
        assertEquals(LESSON_ID, with.lessonId());
        assertEquals(50, with.pointsReward());
        assertEquals(10, with.durationMinutes());
    }

    @Test
    void mapsALessonWithoutTheCorrectAnswerOrTheFeedback() throws Exception {
        var response = mapper.toResponse(lesson(LessonStatus.PUBLISHED));
        var json = JsonMapper.builder().build().writeValueAsString(response);

        assertEquals("Passwords", response.title());
        assertEquals(2, response.version());
        assertEquals(1, response.scenes().size());
        assertEquals("TAP", response.scenes().get(0).interactions().get(0).type());
        assertEquals("lock", response.scenes().get(0).interactions().get(0).config().get("target"));
        assertEquals(70, response.quiz().passingPercentage());
        assertEquals(2, response.quiz().questions().get(0).options().size());
        assertEquals("Tell an adult", response.quiz().questions().get(0).options().get(1).text());
        assertFalse(json.contains("correct"), json);
        assertFalse(json.contains("feedback"), json);
        assertFalse(json.contains("right answer"), json);
        assertTrue(json.contains("Tell an adult"));
    }

    @Test
    void aLessonWithoutQuizHasNullQuiz() {
        var base = lesson(LessonStatus.PUBLISHED);
        var noQuiz = new com.umdc.smartkin.mongo.document.LessonDocument(base.id(), base.slug(), base.topicId(), base.title(), base.description(),
                base.locale(), base.status(), base.version(), base.scenes(), null, base.createdBy(), base.createdAt(), base.updatedAt());

        assertNull(mapper.toResponse(noQuiz).quiz());
    }
}
