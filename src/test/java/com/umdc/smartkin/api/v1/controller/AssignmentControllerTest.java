package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.AssignmentService;
import com.umdc.smartkin.api.v1.to.AssignmentRequest;
import com.umdc.smartkin.api.v1.to.AssignmentResponse;
import com.umdc.smartkin.api.v1.to.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.umdc.smartkin.support.TestData.ACTIVITY_ID;
import static com.umdc.smartkin.support.TestData.CHILD_ID;
import static com.umdc.smartkin.support.TestData.PARENT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AssignmentControllerTest {

    private static final String TOKEN = "session-token-value";
    private static final UUID ASSIGNMENT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private AssignmentService service;
    private AssignmentController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new AssignmentController(service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static AssignmentResponse assignment() {
        return new AssignmentResponse(ASSIGNMENT_ID, CHILD_ID, ACTIVITY_ID, "Passwords", null, PARENT_ID, Instant.parse("2026-10-21T12:00:00Z"), null, null);
    }

    @Test
    void delegatesEveryOperationToTheService() {
        var request = new AssignmentRequest(CHILD_ID, ACTIVITY_ID, null);
        ResponseEntity<AssignmentResponse> created = ResponseEntity.status(HttpStatus.CREATED).body(assignment());
        ResponseEntity<PageResponse<AssignmentResponse>> page = ResponseEntity.ok(new PageResponse<>(0, 20, 0, 0, List.of()));
        when(service.assign(TOKEN, request)).thenReturn(created);
        when(service.list(TOKEN, CHILD_ID, 1, 5)).thenReturn(page);
        when(service.get(TOKEN, ASSIGNMENT_ID)).thenReturn(ResponseEntity.ok(assignment()));

        assertSame(created, controller.assignActivity(TOKEN, request));
        assertSame(page, controller.listAssignments(TOKEN, CHILD_ID, 1, 5));
        assertEquals(ASSIGNMENT_ID, controller.getAssignment(TOKEN, ASSIGNMENT_ID).getBody().assignmentId());
        assertSame(service, controller.getService());
    }

    @Test
    void theApiAnswers501UntilAServiceIsProvided() {
        var api = new AssignmentApi() {};

        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.assignActivity(TOKEN, new AssignmentRequest(CHILD_ID, ACTIVITY_ID, null)).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.listAssignments(TOKEN, null, 0, 20).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.getAssignment(TOKEN, ASSIGNMENT_ID).getStatusCode());
    }

    @Test
    void theHttpLayerAppliesThePagingDefaultsAndMapsBodies() throws Exception {
        when(service.list(TOKEN, null, 0, 20)).thenReturn(ResponseEntity.ok(new PageResponse<>(0, 20, 1, 1, List.of(assignment()))));
        when(service.assign(any(), any())).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(assignment()));
        when(service.get(TOKEN, ASSIGNMENT_ID)).thenReturn(ResponseEntity.ok(assignment()));

        mvc.perform(get("/api/v1/assignments").header("session-token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].activityTitle").value("Passwords"));
        mvc.perform(post("/api/v1/assignments").header("session-token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"childId\":\"" + CHILD_ID + "\",\"activityId\":\"" + ACTIVITY_ID + "\",\"dueDate\":\"2030-01-01T00:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignmentId").value(ASSIGNMENT_ID.toString()));
        mvc.perform(get("/api/v1/assignments/{id}", ASSIGNMENT_ID).header("session-token", TOKEN)).andExpect(status().isOk());
        verify(service).list(TOKEN, null, 0, 20);
    }

    @Test
    void incompleteBodiesAndMissingHeadersNeverReachTheService() throws Exception {
        mvc.perform(post("/api/v1/assignments").header("session-token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"childId\":\"" + CHILD_ID + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/assignments").header("session-token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/assignments")).andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
