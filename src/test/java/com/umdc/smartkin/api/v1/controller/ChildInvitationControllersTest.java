package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.ChildAccountService;
import com.umdc.smartkin.api.v1.service.ChildInvitationService;
import com.umdc.smartkin.api.v1.to.AcceptInvitationRequest;
import com.umdc.smartkin.api.v1.to.ChildAccountRequest;
import com.umdc.smartkin.api.v1.to.ChildAccountResponse;
import com.umdc.smartkin.api.v1.to.ChildResponse;
import com.umdc.smartkin.api.v1.to.InvitationRequest;
import com.umdc.smartkin.api.v1.to.InvitationResponse;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.umdc.smartkin.support.TestData.CHILD_ID;
import static com.umdc.smartkin.support.TestData.PARENT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChildInvitationControllersTest {

    private static final String TOKEN = "session-token-value";
    private static final UUID INVITATION_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final InvitationResponse INVITATION =
            new InvitationResponse(INVITATION_ID, "ABCD-EFGH", "PENDING", "5", null, Instant.parse("2030-01-08T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
    private static final ChildResponse CHILD = new ChildResponse(CHILD_ID, PARENT_ID, "5", null, 0);

    @Mock
    private ChildInvitationService invitationService;
    @Mock
    private ChildAccountService accountService;
    private ChildInvitationController invitationController;
    private ChildAccountController accountController;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        invitationController = new ChildInvitationController(invitationService);
        accountController = new ChildAccountController(accountService);
        mvc = MockMvcBuilders.standaloneSetup(invitationController, accountController, new ChildController(null)).build();
    }

    @Test
    void theInvitationControllerDelegatesToTheService() {
        var request = new InvitationRequest("5", null);
        var accept = new AcceptInvitationRequest("abcd-efgh");
        when(invitationService.create(TOKEN, request)).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(INVITATION));
        when(invitationService.list(TOKEN)).thenReturn(ResponseEntity.ok(List.of(INVITATION)));
        when(invitationService.revoke(TOKEN, INVITATION_ID)).thenReturn(ResponseEntity.noContent().build());
        when(invitationService.accept(TOKEN, accept)).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(CHILD));

        assertEquals(INVITATION, invitationController.createChildInvitation(TOKEN, request).getBody());
        assertEquals(List.of(INVITATION), invitationController.listChildInvitations(TOKEN).getBody());
        assertEquals(HttpStatus.NO_CONTENT, invitationController.revokeChildInvitation(TOKEN, INVITATION_ID).getStatusCode());
        assertEquals(CHILD, invitationController.acceptChildInvitation(TOKEN, accept).getBody());
        assertSame(invitationService, invitationController.getService());
    }

    @Test
    void theAccountControllerDelegatesToTheService() {
        var request = new ChildAccountRequest("Ana", "Roe", LocalDate.of(2016, 3, 1), "ana@smartkin.test", "secret-pass", "3", null);
        var response = new ChildAccountResponse(CHILD, "aroe", "ana@smartkin.test");
        when(accountService.create(TOKEN, request)).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(response));

        assertEquals(response, accountController.createChildAccount(TOKEN, request).getBody());
        assertSame(accountService, accountController.getService());
        assertEquals("ChildAccountRequest{firstname='Ana', lastname='Roe', email='ana@smartkin.test'}", request.toString());
    }

    @Test
    void theApiInterfacesAnswer501UntilAServiceIsProvided() {
        var invitations = new ChildInvitationApi() {};
        var accounts = new ChildAccountApi() {};

        assertEquals(HttpStatus.NOT_IMPLEMENTED, invitations.createChildInvitation(TOKEN, new InvitationRequest(null, null)).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, invitations.listChildInvitations(TOKEN).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, invitations.revokeChildInvitation(TOKEN, INVITATION_ID).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, invitations.acceptChildInvitation(TOKEN, new AcceptInvitationRequest("x")).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED,
                accounts.createChildAccount(TOKEN, new ChildAccountRequest("A", "B", LocalDate.now(), "a@b.co", "12345678", null, null)).getStatusCode());
    }

    @Test
    void theHttpLayerMapsPathsHeadersAndBodies() throws Exception {
        when(invitationService.create(TOKEN, new InvitationRequest("5", null))).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(INVITATION));
        when(invitationService.list(TOKEN)).thenReturn(ResponseEntity.ok(List.of(INVITATION)));
        when(invitationService.revoke(TOKEN, INVITATION_ID)).thenReturn(ResponseEntity.noContent().build());
        when(invitationService.accept(TOKEN, new AcceptInvitationRequest("ABCD-EFGH"))).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(CHILD));

        mvc.perform(post("/api/v1/children/invitations").header("session-token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gradeLevel\":\"5\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ABCD-EFGH"));
        mvc.perform(get("/api/v1/children/invitations").header("session-token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
        mvc.perform(delete("/api/v1/children/invitations/{id}", INVITATION_ID).header("session-token", TOKEN))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/children/invitations/accept").header("session-token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ABCD-EFGH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.childId").value(CHILD_ID.toString()));
    }

    @Test
    void invalidBodiesAreRejectedBeforeTheService() throws Exception {
        mvc.perform(post("/api/v1/children/invitations").header("session-token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gradeLevel\":\"" + "x".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/children/invitations/accept").header("session-token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/children/accounts").header("session-token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstname\":\"Ana\",\"lastname\":\"Roe\",\"dateOfBirth\":\"2016-03-01\",\"email\":\"not-an-email\",\"password\":\"12345678\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/children/accounts").header("session-token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstname\":\"Ana\",\"lastname\":\"Roe\",\"dateOfBirth\":\"2016-03-01\",\"email\":\"a@b.co\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/children/invitations")).andExpect(status().isBadRequest());

        verifyNoInteractions(invitationService, accountService);
    }
}
