package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.QuizAttemptService;
import com.umdc.smartkin.api.v1.to.QuizAnswerRequest;
import com.umdc.smartkin.api.v1.to.QuizAttemptRequest;
import com.umdc.smartkin.api.v1.to.QuizAttemptResponse;
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
class QuizControllerTest {

    private static final String TOKEN = "session-token-value";
    private static final UUID ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private QuizAttemptService service;
    private QuizController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new QuizController(service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static QuizAttemptResponse attempt() {
        return new QuizAttemptResponse(ID, ID, ID, 1, 1, 10, 10, 100, true, Instant.parse("2026-10-21T12:00:00Z"), List.of());
    }

    @Test
    void delegatesEveryOperationToTheService() {
        var request = new QuizAttemptRequest(List.of(new QuizAnswerRequest("q1", List.of("o1"))));
        ResponseEntity<QuizAttemptResponse> created = ResponseEntity.status(HttpStatus.CREATED).body(attempt());
        ResponseEntity<List<QuizAttemptResponse>> list = ResponseEntity.ok(List.of(attempt()));
        when(service.submit(TOKEN, ID, request)).thenReturn(created);
        when(service.list(TOKEN, ID)).thenReturn(list);

        assertSame(created, controller.submitQuizAttempt(TOKEN, ID, request));
        assertSame(list, controller.listQuizAttempts(TOKEN, ID));
        assertSame(service, controller.getService());
    }

    @Test
    void theApiAnswers501UntilAServiceIsProvided() {
        var api = new QuizApi() {};

        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.listQuizAttempts(TOKEN, ID).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.submitQuizAttempt(TOKEN, ID, new QuizAttemptRequest(List.of())).getStatusCode());
    }

    @Test
    void theHttpLayerMapsPathsAndBodies() throws Exception {
        when(service.submit(any(), any(), any())).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(attempt()));
        when(service.list(TOKEN, ID)).thenReturn(ResponseEntity.ok(List.of(attempt())));

        mvc.perform(post("/api/v1/assignments/{id}/quiz-attempts", ID).header("session-token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":\"q1\",\"selectedOptionIds\":[\"o1\"]}]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.percentage").value(100));
        mvc.perform(post("/api/v1/assignments/{id}/quiz-attempts", ID).header("session-token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[]}")).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/assignments/{id}/quiz-attempts", ID).header("session-token", TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].passed").value(true));
        verify(service).submit(TOKEN, ID, new QuizAttemptRequest(List.of(new QuizAnswerRequest("q1", List.of("o1")))));
    }

    @Test
    void invalidBodiesNeverReachTheService() throws Exception {
        for (var body : new String[]{"{}", "{\"answers\":[{\"selectedOptionIds\":[]}]}", "{\"answers\":[{\"questionId\":\"\",\"selectedOptionIds\":[]}]}",
                "{\"answers\":[{\"questionId\":\"q1\"}]}"}) {
            mvc.perform(post("/api/v1/assignments/{id}/quiz-attempts", ID).header("session-token", TOKEN)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/assignments/{id}/quiz-attempts", ID)).andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
