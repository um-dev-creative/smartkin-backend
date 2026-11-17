package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ConfirmCodeRequest;
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

    private UserRegisterServiceImpl service;
    private final ConfirmCodeRequest request = new ConfirmCodeRequest(USER_ID, "1234-5678");

    @BeforeEach
    void setUp() {
        service = new UserRegisterServiceImpl(mercuryClient, mercuryTokens, backboneClient, confirmCodeMapper, mercuryMailer);
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
}
