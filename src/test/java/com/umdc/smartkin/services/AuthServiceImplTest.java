package com.umdc.smartkin.services;

import com.umdc.security.service.SessionJwtService;
import com.umdc.security.to.AuthRequest;
import com.umdc.smartkin.client.backbone.BackboneClient;
import com.umdc.smartkin.client.backbone.to.PrxTokenString;
import com.umdc.smartkin.client.mercury.MercuryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static com.umdc.smartkin.support.TestData.feign;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private SessionJwtService sessionJwtService;
    @Mock
    private BackboneClient backboneClient;
    @Mock
    private MercuryClient mercuryClient;

    private AuthServiceImpl service;
    private String backboneToken;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(sessionJwtService, backboneClient, mercuryClient);
        var payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"uid\":\"" + USER + "\"}").getBytes(StandardCharsets.UTF_8));
        backboneToken = "h." + payload + ".s";
    }

    @Test
    void issuesATokenForAnAlias() {
        when(sessionJwtService.generateSessionToken(eq("alopez"), any())).thenReturn("jwt");

        var response = service.token(new AuthRequest("alopez", "pwd"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("jwt", response.getBody().token());
    }

    @Test
    void rejectsABlankOrMissingAlias() {
        assertEquals(HttpStatus.BAD_REQUEST, service.token(new AuthRequest(" ", "pwd")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, service.token(new AuthRequest(null, "pwd")).getStatusCode());
        verifyNoInteractions(sessionJwtService);
    }

    @Test
    void answersNotAcceptableWhenTheGeneratedTokenIsBlank() {
        when(sessionJwtService.generateSessionToken(any(), any())).thenReturn("");

        assertEquals(HttpStatus.NOT_ACCEPTABLE, service.token(new AuthRequest("alopez", "pwd")).getStatusCode());
    }

    @Test
    void addsTheVerificationStatusWhenMercuryKnowsTheUser() {
        var request = new AuthRequest("alopez", "pwd");
        when(mercuryClient.token(backboneToken, request)).thenReturn(new PrxTokenString("mercury"));
        when(mercuryClient.isVerificationCodeDone("mercury", USER)).thenReturn(true);
        when(sessionJwtService.generateSessionToken(eq("alopez"), any())).thenReturn("jwt");

        var response = service.token(request, backboneToken);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> parameters = ArgumentCaptor.forClass(Map.class);
        verify(sessionJwtService).generateSessionToken(eq("alopez"), parameters.capture());
        assertEquals("true", parameters.getValue().get("vcCompleted"));
        assertEquals(USER.toString(), parameters.getValue().get("uid"));
    }

    @Test
    void marksTheVerificationAsIncompleteWhenMercuryHasNoCode() {
        var request = new AuthRequest("alopez", "pwd");
        when(mercuryClient.token(backboneToken, request)).thenReturn(new PrxTokenString("mercury"));
        when(mercuryClient.isVerificationCodeDone("mercury", USER)).thenThrow(feign(404));
        when(sessionJwtService.generateSessionToken(eq("alopez"), any())).thenReturn("jwt");

        var response = service.token(request, backboneToken);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> parameters = ArgumentCaptor.forClass(Map.class);
        verify(sessionJwtService).generateSessionToken(eq("alopez"), parameters.capture());
        assertEquals("false", parameters.getValue().get("vcCompleted"));
    }

    @Test
    void rejectsABlankAliasWhenCheckingTheVerification() {
        assertEquals(HttpStatus.BAD_REQUEST, service.token(new AuthRequest("", "pwd"), backboneToken).getStatusCode());
        verifyNoInteractions(mercuryClient);
    }

    @Test
    void answersNotAcceptableWhenTheVerifiedTokenIsBlank() {
        var request = new AuthRequest("alopez", "pwd");
        when(mercuryClient.token(backboneToken, request)).thenReturn(new PrxTokenString("mercury"));
        when(mercuryClient.isVerificationCodeDone("mercury", USER)).thenReturn(false);
        when(sessionJwtService.generateSessionToken(any(), any())).thenReturn("");

        assertEquals(HttpStatus.NOT_ACCEPTABLE, service.token(request, backboneToken).getStatusCode());
    }

    @Test
    void delegatesTheValidationToBackbone() {
        when(backboneClient.validate("ok")).thenReturn(true);
        when(backboneClient.validate("bad")).thenReturn(false);

        assertTrue(service.validate("ok"));
        assertFalse(service.validate("bad"));
    }
}
