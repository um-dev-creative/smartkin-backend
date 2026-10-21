package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.CatalogService;
import com.umdc.smartkin.api.v1.to.ActivityResponse;
import com.umdc.smartkin.api.v1.to.TopicResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static com.umdc.smartkin.support.TestData.ACTIVITY_ID;
import static com.umdc.smartkin.support.TestData.LESSON_ID;
import static com.umdc.smartkin.support.TestData.TOPIC_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CatalogControllerTest {

    @Mock
    private CatalogService catalogService;
    private CatalogController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new CatalogController(catalogService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void delegatesEveryOperationToTheService() {
        ResponseEntity<List<TopicResponse>> topics = ResponseEntity.ok(List.of());
        ResponseEntity<List<ActivityResponse>> activities = ResponseEntity.ok(List.of());
        when(catalogService.topics()).thenReturn(topics);
        when(catalogService.activities(TOPIC_ID)).thenReturn(activities);

        assertSame(topics, controller.listTopics());
        assertSame(activities, controller.listActivities(TOPIC_ID));
        controller.listTopicLessons(TOPIC_ID);
        controller.getLesson(LESSON_ID);
        controller.getActivity(ACTIVITY_ID);

        verify(catalogService).topicLessons(TOPIC_ID);
        verify(catalogService).lesson(LESSON_ID);
        verify(catalogService).activity(ACTIVITY_ID);
        assertSame(catalogService, controller.getService());
    }

    @Test
    void theApiAnswers501UntilAServiceIsProvided() {
        var api = new CatalogApi() {};

        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.listTopics().getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.listTopicLessons(TOPIC_ID).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.getLesson(LESSON_ID).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.listActivities(null).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.getActivity(ACTIVITY_ID).getStatusCode());
    }

    @Test
    void theHttpLayerMapsPathsAndTheOptionalTopicFilter() throws Exception {
        when(catalogService.topics()).thenReturn(ResponseEntity.ok(List.of(new TopicResponse(TOPIC_ID, "Screen time", null, null, 8, 14))));
        when(catalogService.activities(null)).thenReturn(ResponseEntity.ok(List.of()));
        when(catalogService.activities(TOPIC_ID)).thenReturn(ResponseEntity.ok(List.of(
                new ActivityResponse(ACTIVITY_ID, TOPIC_ID, LESSON_ID, "Passwords", "d", 10, 50))));

        mvc.perform(get("/api/v1/topics")).andExpect(status().isOk()).andExpect(jsonPath("$[0].topicId").value(TOPIC_ID.toString()));
        mvc.perform(get("/api/v1/activities")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/activities").param("topicId", TOPIC_ID.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].lessonId").value(LESSON_ID.toString()));
        mvc.perform(get("/api/v1/topics/{id}/lessons", TOPIC_ID));
        mvc.perform(get("/api/v1/lessons/{id}", LESSON_ID));
        mvc.perform(get("/api/v1/activities/{id}", ACTIVITY_ID));
        mvc.perform(get("/api/v1/lessons/not-a-uuid")).andExpect(status().isBadRequest());

        verify(catalogService).topicLessons(TOPIC_ID);
        verify(catalogService).lesson(LESSON_ID);
        verify(catalogService).activity(ACTIVITY_ID);
    }
}
