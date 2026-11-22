package com.umdc.smartkin.api.v1.controller;

import com.umdc.security.annotation.SkipSessionValidation;
import com.umdc.smartkin.api.v1.service.UserRegisterService;
import com.umdc.smartkin.api.v1.to.ResendVerificationRequest;
import com.umdc.smartkin.api.v1.to.VerificationStatusResponse;
import com.umdc.smartkin.api.v1.to.VerifyEmailRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// The two endpoints a visitor uses to verify a new account: no session, the account is named by its e-mail.
class UserRegisterPublicEndpointsTest {

    private UserRegisterService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(UserRegisterService.class);
        mvc = MockMvcBuilders.standaloneSetup(new UserRegisterController(service)).build();
    }

    @Test
    void verifyGoesThroughTheServiceAndAnswersWhatItAnswers() throws Exception {
        var request = new VerifyEmailRequest("ana@smartkin.test", "1234-5678");
        when(service.verifyEmail(request)).thenReturn(ResponseEntity.ok().build());

        mvc.perform(post("/api/v1/user-register/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@smartkin.test\",\"verificationCode\":\"1234-5678\"}")).andExpect(status().isOk());

        verify(service).verifyEmail(request);
    }

    @Test
    void resendGoesThroughTheServiceAndAnswersWhatItAnswers() throws Exception {
        var request = new ResendVerificationRequest("ana@smartkin.test");
        when(service.resendVerification(request)).thenReturn(ResponseEntity.accepted().build());

        mvc.perform(post("/api/v1/user-register/resend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@smartkin.test\"}")).andExpect(status().isAccepted());

        verify(service).resendVerification(request);
    }

    @Test
    void aMalformedRequestIsRefusedBeforeReachingTheService() throws Exception {
        mvc.perform(post("/api/v1/user-register/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"verificationCode\":\"1234-5678\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/user-register/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@smartkin.test\",\"verificationCode\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/user-register/resend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\"}")).andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void bothEndpointsSkipTheSessionBecauseTheVisitorCannotSignInYet() throws Exception {
        for (var method : new String[]{"verifyEmail", "resendVerification"}) {
            var parameter = method.equals("verifyEmail") ? VerifyEmailRequest.class : ResendVerificationRequest.class;
            var annotation = UserRegisterController.class.getMethod(method, parameter).getAnnotation(SkipSessionValidation.class);
            assertNotNull(annotation, method);
            assertTrue(annotation.value().contains("no session"), method);
        }
    }

    @Test
    void theRequestDoesNotPrintTheCode() {
        assertEquals("VerifyEmailRequest{email='a@b.co', verificationCode='<hidden>'}", new VerifyEmailRequest("a@b.co", "1234-5678").toString());
    }

    @Test
    void apiAndServiceDefaultsAnswerNotImplemented() {
        var api = new UserRegisterApi() { };
        var empty = new UserRegisterService() { };

        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.verifyEmail(new VerifyEmailRequest("a@b.co", "1")).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.resendVerification(new ResendVerificationRequest("a@b.co")).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, empty.verifyEmail(null).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, empty.resendVerification(null).getStatusCode());
    }

    @Test
    void theStatusReadsTheSessionTokenAndAnswersTheService() throws Exception {
        when(service.status("tok")).thenReturn(ResponseEntity.ok(new VerificationStatusResponse(false)));

        mvc.perform(get("/api/v1/user-register/status").header("session-token", "tok"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(false));

        verify(service).status("tok");
    }

    @Test
    void confirmAndResendReadTheSingleSessionToken() throws Exception {
        when(service.resendCode(org.mockito.ArgumentMatchers.eq("tok"), org.mockito.ArgumentMatchers.any())).thenReturn(ResponseEntity.accepted().build());
        when(service.confirmCode(org.mockito.ArgumentMatchers.eq("tok"), org.mockito.ArgumentMatchers.any())).thenReturn(ResponseEntity.ok().build());

        mvc.perform(post("/api/v1/user-register/11111111-1111-1111-1111-111111111111/resend").header("session-token", "tok"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/user-register").header("session-token", "tok").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"11111111-1111-1111-1111-111111111111\",\"verificationCode\":\"1234-5678\"}"))
                .andExpect(status().isOk());
    }
}
