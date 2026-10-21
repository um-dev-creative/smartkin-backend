package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.RewardsService;
import com.umdc.smartkin.api.v1.to.ChildAchievementResponse;
import com.umdc.smartkin.api.v1.to.PageResponse;
import com.umdc.smartkin.api.v1.to.PointsSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.umdc.smartkin.support.TestData.CHILD_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RewardsControllerTest {

    private static final String TOKEN = "session-token-value";

    @Mock
    private RewardsService service;
    private RewardsController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new RewardsController(service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void delegatesEveryOperationToTheService() {
        ResponseEntity<PointsSummaryResponse> points = ResponseEntity.ok(new PointsSummaryResponse(CHILD_ID, 10, new PageResponse<>(0, 20, 0, 0, List.of())));
        ResponseEntity<List<ChildAchievementResponse>> achievements = ResponseEntity.ok(List.of());
        when(service.points(TOKEN, CHILD_ID, 2, 5)).thenReturn(points);
        when(service.achievements(TOKEN, CHILD_ID)).thenReturn(achievements);

        assertSame(points, controller.getChildPoints(TOKEN, CHILD_ID, 2, 5));
        assertSame(achievements, controller.listChildAchievements(TOKEN, CHILD_ID));
        assertSame(service, controller.getService());
    }

    @Test
    void theApiAnswers501UntilAServiceIsProvided() {
        var api = new RewardsApi() {};

        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.getChildPoints(TOKEN, CHILD_ID, 0, 20).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.listChildAchievements(TOKEN, CHILD_ID).getStatusCode());
    }

    @Test
    void theHttpLayerAppliesThePagingDefaults() throws Exception {
        when(service.points(TOKEN, CHILD_ID, 0, 20)).thenReturn(ResponseEntity.ok(
                new PointsSummaryResponse(CHILD_ID, 75, new PageResponse<>(0, 20, 0, 0, List.of()))));
        when(service.achievements(TOKEN, CHILD_ID)).thenReturn(ResponseEntity.ok(List.of(
                new ChildAchievementResponse(UUID.randomUUID(), UUID.randomUUID(), "First lesson", "d", null, 10, Instant.parse("2026-10-21T12:00:00Z")))));

        mvc.perform(get("/api/v1/children/{id}/points", CHILD_ID).header("session-token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(75))
                .andExpect(jsonPath("$.transactions.size").value(20));
        mvc.perform(get("/api/v1/children/{id}/achievements", CHILD_ID).header("session-token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("First lesson"));
        mvc.perform(get("/api/v1/children/{id}/points", CHILD_ID)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/children/not-a-uuid/achievements").header("session-token", TOKEN)).andExpect(status().isBadRequest());
        verify(service).points(TOKEN, CHILD_ID, 0, 20);
    }
}
