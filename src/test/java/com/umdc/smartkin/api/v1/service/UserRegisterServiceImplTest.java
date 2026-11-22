package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ConfirmCodeRequest;
import com.umdc.smartkin.api.v1.to.ResendVerificationRequest;
import com.umdc.smartkin.api.v1.to.VerifyEmailRequest;
import com.umdc.smartkin.client.backbone.to.BackboneEmailLookupRequest;
import com.umdc.smartkin.client.backbone.to.BackboneEmailLookupResponse;
import com.umdc.smartkin.client.backbone.BackboneClient;
import com.umdc.smartkin.client.mercury.MailDeliveryException;
import com.umdc.smartkin.client.mercury.MercuryClient;
import com.umdc.smartkin.client.mercury.MercuryMailer;
import com.umdc.smartkin.client.mercury.MercuryTokens;
import com.umdc.smartkin.client.mercury.to.VerificationCodeRequest;
import com.umdc.smartkin.mapper.ConfirmCodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.umdc.smartkin.support.TestData.APPLICATION_ID;
import static com.umdc.smartkin.support.TestData.USER_ID;
import static com.umdc.smartkin.support.TestData.feign;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegisterServiceImplTest {

    private static final String SESSION_BKD = com.umdc.smartkin.support.TestData.tokenFor(USER_ID);
    private static final String MERCURY_TOKEN = "mercury-token";

    @Mock
    private MercuryClient mercuryClient;
    @Mock
    private MercuryTokens mercuryTokens;
    @Mock
    private BackboneClient backboneClient;
    @Mock
    private ConfirmCodeMapper confirmCodeMapper;
    @Mock
    private MercuryMailer mercuryMailer;
    @Mock
    private com.umdc.smartkin.security.verification.VerificationStatus verificationStatus;

    private UserRegisterServiceImpl service;
    private Instant now;
    private final ConfirmCodeRequest request = new ConfirmCodeRequest(USER_ID, "1234-5678");

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-11-21T22:00:00Z");
        var clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        };
        service = new UserRegisterServiceImpl(mercuryClient, mercuryTokens, backboneClient, confirmCodeMapper, mercuryMailer, clock, verificationStatus);
        ReflectionTestUtils.setField(service, "applicationId", APPLICATION_ID);
    }

    @SuppressWarnings("unchecked")
    private void runsCallsWithTheMercuryToken() {
        when(mercuryTokens.withToken(any())).thenAnswer(call -> ((Function<String, Object>) call.getArgument(0)).apply(MERCURY_TOKEN));
    }

    @Test
    void confirmsTheCodeWithTheMercuryTokenAndActivatesTheUser() {
        var verificationRequest = mock(VerificationCodeRequest.class);
        runsCallsWithTheMercuryToken();
        when(confirmCodeMapper.toVerificationCodeRequest(request, APPLICATION_ID)).thenReturn(verificationRequest);
        when(mercuryClient.confirmCode(MERCURY_TOKEN, verificationRequest)).thenReturn(ResponseEntity.accepted().build());

        var response = service.confirmCode(SESSION_BKD, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mercuryClient).confirmCode(MERCURY_TOKEN, verificationRequest);
        verify(backboneClient).activateUser(APPLICATION_ID, USER_ID);
    }

    @Test
    void relaysTheMercuryStatusMessageAndAttemptsAndDoesNotActivate() {
        when(mercuryTokens.withToken(any())).thenThrow(feign(406, Map.of("message", List.of("Invalid code"), "Attempts-Remaining", List.of("2"))));

        var response = service.confirmCode(SESSION_BKD, request);

        assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());
        assertEquals("Invalid code", response.getHeaders().getFirst("message"));
        assertEquals("2", response.getHeaders().getFirst("Attempts-Remaining"));
        verifyNoInteractions(backboneClient);
    }

    @Test
    void relaysTheStatusWithAnEmptyMessageWhenMercuryDoesNotSendOne() {
        when(mercuryTokens.withToken(any())).thenThrow(feign(500));

        var response = service.confirmCode(SESSION_BKD, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("", response.getHeaders().getFirst("message"));
        assertNull(response.getHeaders().getFirst("Attempts-Remaining"));
    }

    @Test
    void activatesWhenMercuryHasNoCodeButTheLatestOneWasVerified() {
        when(confirmCodeMapper.toVerificationCodeRequest(request, APPLICATION_ID)).thenReturn(mock(VerificationCodeRequest.class));
        runsCallsWithTheMercuryToken();
        when(mercuryClient.confirmCode(any(), any())).thenThrow(feign(404));
        when(mercuryClient.isVerificationCodeDone(MERCURY_TOKEN, USER_ID)).thenReturn(true);

        var response = service.confirmCode(SESSION_BKD, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(backboneClient).activateUser(APPLICATION_ID, USER_ID);
    }

    @Test
    void answersNotFoundWhenThereIsNoCodeAndNothingWasVerified() {
        when(confirmCodeMapper.toVerificationCodeRequest(request, APPLICATION_ID)).thenReturn(mock(VerificationCodeRequest.class));
        runsCallsWithTheMercuryToken();
        when(mercuryClient.confirmCode(any(), any())).thenThrow(feign(404));
        when(mercuryClient.isVerificationCodeDone(MERCURY_TOKEN, USER_ID)).thenReturn(false);

        var response = service.confirmCode(SESSION_BKD, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verifyNoInteractions(backboneClient);
    }

    @Test
    void answersNotFoundWhenTheLatestStatusCannotBeRead() {
        when(confirmCodeMapper.toVerificationCodeRequest(request, APPLICATION_ID)).thenReturn(mock(VerificationCodeRequest.class));
        runsCallsWithTheMercuryToken();
        when(mercuryClient.confirmCode(any(), any())).thenThrow(feign(404));
        when(mercuryClient.isVerificationCodeDone(MERCURY_TOKEN, USER_ID)).thenThrow(feign(404));

        var response = service.confirmCode(SESSION_BKD, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void answersBadGatewayWhenBackboneCannotActivateAfterARightCode() {
        when(confirmCodeMapper.toVerificationCodeRequest(request, APPLICATION_ID)).thenReturn(mock(VerificationCodeRequest.class));
        runsCallsWithTheMercuryToken();
        when(mercuryClient.confirmCode(any(), any())).thenReturn(ResponseEntity.accepted().build());
        when(backboneClient.activateUser(APPLICATION_ID, USER_ID)).thenThrow(feign(403));

        var response = service.confirmCode(SESSION_BKD, request);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
    }

    @Test
    void refusesToConfirmTheCodeOfAnotherUser() {
        var other = new ConfirmCodeRequest(java.util.UUID.randomUUID(), "1234-5678");

        assertEquals(HttpStatus.FORBIDDEN, service.confirmCode(SESSION_BKD, other).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, service.confirmCode("garbage", request).getStatusCode());
        verifyNoInteractions(mercuryClient, backboneClient);
    }

    @Test
    void resendsANewCodeToTheEmailBackboneHolds() {
        runsCallsWithTheMercuryToken();
        when(mercuryClient.isVerificationCodeDone(MERCURY_TOKEN, USER_ID)).thenReturn(false);
        when(backboneClient.findUserById(USER_ID)).thenReturn(com.umdc.smartkin.support.TestData.backboneUser(
                com.umdc.smartkin.support.TestData.person("Phone"), List.of(), List.of()));

        var response = service.resendCode(SESSION_BKD, USER_ID);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(mercuryMailer).resendVerificationCode(USER_ID, "ana@smartkin.test", "Ana");
    }

    @Test
    void resendTreatsAnUnknownStatusAsNotVerified() {
        when(mercuryTokens.withToken(any())).thenThrow(feign(404));
        when(backboneClient.findUserById(USER_ID)).thenReturn(com.umdc.smartkin.support.TestData.backboneUser(
                com.umdc.smartkin.support.TestData.person("Phone"), List.of(), List.of()));

        assertEquals(HttpStatus.ACCEPTED, service.resendCode(SESSION_BKD, USER_ID).getStatusCode());
    }

    @Test
    void resendAnswersConflictWhenTheEmailIsAlreadyVerified() {
        runsCallsWithTheMercuryToken();
        when(mercuryClient.isVerificationCodeDone(MERCURY_TOKEN, USER_ID)).thenReturn(true);

        assertEquals(HttpStatus.CONFLICT, service.resendCode(SESSION_BKD, USER_ID).getStatusCode());
        verifyNoInteractions(mercuryMailer);
    }

    @Test
    void resendIsRefusedForAnotherUser() {
        assertEquals(HttpStatus.FORBIDDEN, service.resendCode(SESSION_BKD, java.util.UUID.randomUUID()).getStatusCode());
        verifyNoInteractions(mercuryMailer, backboneClient);
    }

    @Test
    void resendRelaysBackboneFailures() {
        runsCallsWithTheMercuryToken();
        when(mercuryClient.isVerificationCodeDone(MERCURY_TOKEN, USER_ID)).thenReturn(false);
        when(backboneClient.findUserById(USER_ID)).thenThrow(feign(404)).thenThrow(feign(503));

        assertEquals(HttpStatus.NOT_FOUND, service.resendCode(SESSION_BKD, USER_ID).getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, service.resendCode(SESSION_BKD, USER_ID).getStatusCode());
    }

    @Test
    void resendAnswers503WhenMercuryDoesNotTakeTheMessage() {
        runsCallsWithTheMercuryToken();
        when(mercuryClient.isVerificationCodeDone(MERCURY_TOKEN, USER_ID)).thenReturn(false);
        when(backboneClient.findUserById(USER_ID)).thenReturn(com.umdc.smartkin.support.TestData.backboneUser(
                com.umdc.smartkin.support.TestData.person("Phone"), List.of(), List.of()));
        doThrow(new MailDeliveryException("down")).when(mercuryMailer).resendVerificationCode(any(), any(), any());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, service.resendCode(SESSION_BKD, USER_ID).getStatusCode());
    }

    private static final String EMAIL = "ana@smartkin.test";
    private final VerifyEmailRequest verifyRequest = new VerifyEmailRequest(" Ana@SmartKin.test ", "1234-5678");

    private void ownerIs(boolean active) {
        org.mockito.Mockito.doReturn(new BackboneEmailLookupResponse(USER_ID, EMAIL, "Ana", active))
                .when(backboneClient).lookupByEmail(APPLICATION_ID, new BackboneEmailLookupRequest(EMAIL));
    }

    @Test
    void verifiesTheEmailOfAPendingAccountAndActivatesIt() {
        ownerIs(false);
        runsCallsWithTheMercuryToken();
        when(confirmCodeMapper.toVerificationCodeRequest(new ConfirmCodeRequest(USER_ID, "1234-5678"), APPLICATION_ID)).thenReturn(mock(VerificationCodeRequest.class));
        when(mercuryClient.confirmCode(any(), any())).thenReturn(ResponseEntity.accepted().build());

        var response = service.verifyEmail(verifyRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(backboneClient).activateUser(APPLICATION_ID, USER_ID);
    }

    @Test
    void verifyAnswersTheSameNotValidForAnUnknownEmailAndForAnActiveAccount() {
        doThrow(feign(404)).when(backboneClient).lookupByEmail(any(), any());
        var unknown = service.verifyEmail(verifyRequest);
        ownerIs(true);
        var active = service.verifyEmail(verifyRequest);

        assertEquals(HttpStatus.NOT_ACCEPTABLE, unknown.getStatusCode());
        assertEquals(HttpStatus.NOT_ACCEPTABLE, active.getStatusCode());
        assertEquals(UserRegisterServiceImpl.CODE_NOT_VALID_MESSAGE, unknown.getHeaders().getFirst("message-error"));
        assertNull(unknown.getHeaders().getFirst("Attempts-Remaining"));
        verifyNoInteractions(mercuryClient);
        verify(backboneClient, never()).activateUser(any(), any());
    }

    @Test
    void verifyTellsTheAttemptsLeftOfAWrongCode() {
        ownerIs(false);
        when(confirmCodeMapper.toVerificationCodeRequest(any(), any())).thenReturn(mock(VerificationCodeRequest.class));
        when(mercuryTokens.withToken(any())).thenThrow(feign(406, Map.of("Attempts-Remaining", List.of("2"))));

        var response = service.verifyEmail(verifyRequest);

        assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());
        assertEquals("2", response.getHeaders().getFirst("Attempts-Remaining"));
        verify(backboneClient, never()).activateUser(any(), any());
    }

    @Test
    void verifyTellsZeroAttemptsWhenMercuryDoesNotSayHowManyAreLeft() {
        ownerIs(false);
        when(confirmCodeMapper.toVerificationCodeRequest(any(), any())).thenReturn(mock(VerificationCodeRequest.class));
        when(mercuryTokens.withToken(any())).thenThrow(feign(406));

        assertEquals("0", service.verifyEmail(verifyRequest).getHeaders().getFirst("Attempts-Remaining"));
    }

    @Test
    void verifyRecoversAnActivationThatFailedAfterARightCode() {
        ownerIs(false);
        runsCallsWithTheMercuryToken();
        when(confirmCodeMapper.toVerificationCodeRequest(any(), any())).thenReturn(mock(VerificationCodeRequest.class));
        when(mercuryClient.confirmCode(any(), any())).thenThrow(feign(404));
        when(mercuryClient.isVerificationCodeDone(MERCURY_TOKEN, USER_ID)).thenReturn(true);

        assertEquals(HttpStatus.OK, service.verifyEmail(verifyRequest).getStatusCode());
        verify(backboneClient).activateUser(APPLICATION_ID, USER_ID);
    }

    @Test
    void verifyAnswersNotValidWhenThereIsNoCodeAndNothingWasVerified() {
        ownerIs(false);
        runsCallsWithTheMercuryToken();
        when(confirmCodeMapper.toVerificationCodeRequest(any(), any())).thenReturn(mock(VerificationCodeRequest.class));
        when(mercuryClient.confirmCode(any(), any())).thenThrow(feign(404));
        when(mercuryClient.isVerificationCodeDone(MERCURY_TOKEN, USER_ID)).thenReturn(false);

        var response = service.verifyEmail(verifyRequest);

        assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());
        assertEquals("0", response.getHeaders().getFirst("Attempts-Remaining"));
        verify(backboneClient, never()).activateUser(any(), any());
    }

    @Test
    void verifyAnswersBadGatewayWhenMercuryFails() {
        ownerIs(false);
        when(confirmCodeMapper.toVerificationCodeRequest(any(), any())).thenReturn(mock(VerificationCodeRequest.class));
        when(mercuryTokens.withToken(any())).thenThrow(feign(403));

        var response = service.verifyEmail(verifyRequest);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals(com.umdc.smartkin.constant.SmartKinAppConstants.VERIFICATION_UNAVAILABLE_MESSAGE, response.getHeaders().getFirst("message-error"));
    }

    @Test
    void verifyAnswersBadGatewayWhenBackboneCannotBeAskedWhoOwnsTheEmail() {
        when(backboneClient.lookupByEmail(any(), any())).thenThrow(feign(403));

        assertEquals(HttpStatus.BAD_GATEWAY, service.verifyEmail(verifyRequest).getStatusCode());
        verifyNoInteractions(mercuryClient);
    }

    @Test
    void verifyAnswersBadGatewayWhenBackboneCannotActivateAfterARightCode() {
        ownerIs(false);
        runsCallsWithTheMercuryToken();
        when(confirmCodeMapper.toVerificationCodeRequest(any(), any())).thenReturn(mock(VerificationCodeRequest.class));
        when(mercuryClient.confirmCode(any(), any())).thenReturn(ResponseEntity.accepted().build());
        when(backboneClient.activateUser(APPLICATION_ID, USER_ID)).thenThrow(feign(403));

        assertEquals(HttpStatus.BAD_GATEWAY, service.verifyEmail(verifyRequest).getStatusCode());
    }

    @Test
    void resendByEmailSendsANewCodeToAPendingAccount() {
        ownerIs(false);

        var response = service.resendVerification(new ResendVerificationRequest(" Ana@SmartKin.test "));

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(mercuryMailer).resendVerificationCode(USER_ID, EMAIL, "Ana");
    }

    @Test
    void resendByEmailAnswers202WithoutSendingForAnUnknownEmailAnActiveAccountOrAFailure() {
        var request = new ResendVerificationRequest(EMAIL);
        doThrow(feign(404)).when(backboneClient).lookupByEmail(any(), any());
        assertEquals(HttpStatus.ACCEPTED, service.resendVerification(request).getStatusCode());

        now = now.plus(Duration.ofMinutes(2));
        ownerIs(true);
        assertEquals(HttpStatus.ACCEPTED, service.resendVerification(request).getStatusCode());

        now = now.plus(Duration.ofMinutes(2));
        doThrow(feign(500)).when(backboneClient).lookupByEmail(any(), any());
        assertEquals(HttpStatus.ACCEPTED, service.resendVerification(request).getStatusCode());
        verifyNoInteractions(mercuryMailer);
    }

    @Test
    void resendByEmailAnswers202WhenMercuryDoesNotTakeTheMessage() {
        ownerIs(false);
        doThrow(new MailDeliveryException("down")).when(mercuryMailer).resendVerificationCode(USER_ID, EMAIL, "Ana");

        assertEquals(HttpStatus.ACCEPTED, service.resendVerification(new ResendVerificationRequest(EMAIL)).getStatusCode());
    }

    @Test
    void resendByEmailSendsOneCodePerEmailPerMinute() {
        ownerIs(false);
        var request = new ResendVerificationRequest(EMAIL);

        service.resendVerification(request);
        service.resendVerification(request);
        verify(mercuryMailer, org.mockito.Mockito.times(1)).resendVerificationCode(USER_ID, EMAIL, "Ana");

        now = now.plus(UserRegisterServiceImpl.RESEND_COOLDOWN);
        service.resendVerification(request);
        verify(mercuryMailer, org.mockito.Mockito.times(2)).resendVerificationCode(USER_ID, EMAIL, "Ana");
    }

    @Test
    void theStatusSaysWhetherTheCallerVerified() {
        when(verificationStatus.isVerified(USER_ID)).thenReturn(true);

        var response = service.status(SESSION_BKD);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, response.getBody().verified());
    }

    @Test
    void theStatusOfATokenWithoutUserIsRefused() {
        var response = service.status("not-a-jwt");

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(verificationStatus);
    }
}
