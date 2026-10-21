package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.ProgressService;
import com.umdc.smartkin.api.v1.to.FeedbackRequest;
import com.umdc.smartkin.api.v1.to.ProgressRequest;
import com.umdc.smartkin.api.v1.to.ProgressResponse;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProgressControllerTest {

    private static final String TOKEN = "session-token-value";
    private static final UUID ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private ProgressService service;
    private ProgressController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new ProgressController(service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static ProgressResponse progress(int percentage) {
        return new ProgressResponse(ID, percentage, null, null, null, null, null);
    }

    @Test
    void delegatesEveryOperationToTheService() {
        var update = new ProgressRequest(40);
        var feedback = new FeedbackRequest("Well done");
        ResponseEntity<ProgressResponse> ok = ResponseEntity.ok(progress(40));
        when(service.get(TOKEN, ID)).thenReturn(ok);
        when(service.update(TOKEN, ID, update)).thenReturn(ok);
        when(service.feedback(TOKEN, ID, feedback)).thenReturn(ok);

        assertSame(ok, controller.getProgress(TOKEN, ID));
        assertSame(ok, controller.updateProgress(TOKEN, ID, update));
        assertSame(ok, controller.putParentFeedback(TOKEN, ID, feedback));
        assertSame(service, controller.getService());
    }

    @Test
    void theApiAnswers501UntilAServiceIsProvided() {
        var api = new ProgressApi() {};

        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.getProgress(TOKEN, ID).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.updateProgress(TOKEN, ID, new ProgressRequest(1)).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.putParentFeedback(TOKEN, ID, new FeedbackRequest("x")).getStatusCode());
    }

    @Test
    void theHttpLayerMapsPathsAndBodies() throws Exception {
        when(service.get(TOKEN, ID)).thenReturn(ResponseEntity.ok(progress(10)));
        when(service.update(any(), any(), any())).thenReturn(ResponseEntity.ok(progress(55)));
        when(service.feedback(any(), any(), any())).thenReturn(ResponseEntity.ok(progress(55)));

        mvc.perform(get("/api/v1/assignments/{id}/progress", ID).header("session-token", TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completionPercentage").value(10));
        mvc.perform(put("/api/v1/assignments/{id}/progress", ID).header("session-token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completionPercentage\":55}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completionPercentage").value(55));
        mvc.perform(put("/api/v1/assignments/{id}/feedback", ID).header("session-token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"parentFeedback\":\"Great\"}"))
                .andExpect(status().isOk());
        verify(service).update(TOKEN, ID, new ProgressRequest(55));
        verify(service).feedback(TOKEN, ID, new FeedbackRequest("Great"));
    }

    @Test
    void invalidBodiesNeverReachTheService() throws Exception {
        for (var body : new String[]{"{\"completionPercentage\":101}", "{\"completionPercentage\":-1}", "{}"}) {
            mvc.perform(put("/api/v1/assignments/{id}/progress", ID).header("session-token", TOKEN)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        for (var body : new String[]{"{\"parentFeedback\":\"\"}", "{\"parentFeedback\":\"   \"}", "{}"}) {
            mvc.perform(put("/api/v1/assignments/{id}/feedback", ID).header("session-token", TOKEN)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/assignments/{id}/progress", ID)).andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
