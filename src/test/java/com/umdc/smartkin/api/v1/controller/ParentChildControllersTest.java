package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.ChildService;
import com.umdc.smartkin.api.v1.service.ParentService;
import com.umdc.smartkin.api.v1.to.ChildResponse;
import com.umdc.smartkin.api.v1.to.ChildUpdateRequest;
import com.umdc.smartkin.api.v1.to.ParentProfileRequest;
import com.umdc.smartkin.api.v1.to.ParentProfileResponse;
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

import java.util.List;

import static com.umdc.smartkin.support.TestData.CHILD_ID;
import static com.umdc.smartkin.support.TestData.PARENT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ParentChildControllersTest {

    private static final String TOKEN = "session-token-value";

    @Mock
    private ParentService parentService;
    @Mock
    private ChildService childService;
    private MockMvc mvc;
    private ParentController parentController;
    private ChildController childController;

    @BeforeEach
    void setUp() {
        parentController = new ParentController(parentService);
        childController = new ChildController(childService);
        mvc = MockMvcBuilders.standaloneSetup(parentController, childController).build();
    }

    @Test
    void parentControllerDelegatesToTheService() {
        var request = new ParentProfileRequest("+1", "Elm", "p.png");
        ResponseEntity<ParentProfileResponse> profile = ResponseEntity.ok(new ParentProfileResponse(PARENT_ID, "+1", "Elm", "p.png"));
        when(parentService.getMe(TOKEN)).thenReturn(profile);
        when(parentService.putMe(TOKEN, request)).thenReturn(profile);

        assertSame(profile, parentController.getMyParentProfile(TOKEN));
        assertSame(profile, parentController.putMyParentProfile(TOKEN, request));
        assertSame(parentService, parentController.getService());
    }

    @Test
    void childControllerDelegatesToTheService() {
        var child = new ChildResponse(CHILD_ID, PARENT_ID, "5", "fox.png", 0);
        var updateRequest = new ChildUpdateRequest("6", null);
        when(childService.list(TOKEN)).thenReturn(ResponseEntity.ok(List.of(child)));
        when(childService.get(TOKEN, CHILD_ID)).thenReturn(ResponseEntity.ok(child));
        when(childService.update(TOKEN, CHILD_ID, updateRequest)).thenReturn(ResponseEntity.ok(child));

        assertEquals(List.of(child), childController.listMyChildren(TOKEN).getBody());
        assertEquals(child, childController.getChild(TOKEN, CHILD_ID).getBody());
        assertEquals(child, childController.updateChild(TOKEN, CHILD_ID, updateRequest).getBody());
        assertSame(childService, childController.getService());
    }

    @Test
    void theApiInterfacesAnswer501UntilAServiceIsProvided() {
        var parentApi = new ParentApi() {};
        var childApi = new ChildApi() {};

        assertEquals(HttpStatus.NOT_IMPLEMENTED, parentApi.getMyParentProfile(TOKEN).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, parentApi.putMyParentProfile(TOKEN, new ParentProfileRequest(null, null, null)).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, childApi.listMyChildren(TOKEN).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, childApi.getChild(TOKEN, CHILD_ID).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, childApi.updateChild(TOKEN, CHILD_ID, new ChildUpdateRequest(null, null)).getStatusCode());
    }

    @Test
    void theHttpLayerMapsPathsHeadersAndBodies() throws Exception {
        var child = new ChildResponse(CHILD_ID, PARENT_ID, "5", null, 12);
        when(childService.get(TOKEN, CHILD_ID)).thenReturn(ResponseEntity.ok(child));
        when(parentService.getMe(TOKEN)).thenReturn(ResponseEntity.ok(new ParentProfileResponse(PARENT_ID, null, null, null)));

        mvc.perform(get("/api/v1/children/{id}", CHILD_ID).header("session-token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.childId").value(CHILD_ID.toString()))
                .andExpect(jsonPath("$.totalPoints").value(12));
        mvc.perform(get("/api/v1/parents/me").header("session-token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(PARENT_ID.toString()));
    }

    @Test
    void invalidBodiesAndMissingHeadersAreRejectedBeforeTheService() throws Exception {
        mvc.perform(put("/api/v1/parents/me").header("session-token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + "9".repeat(21) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/children/{id}", CHILD_ID).header("session-token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatar\":\"" + "a".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/children")).andExpect(status().isBadRequest());

        verifyNoInteractions(childService, parentService);
    }
}
