package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ForgotPasswordRequest;
import com.umdc.smartkin.api.v1.to.ResetPasswordRequest;
import com.umdc.smartkin.client.backbone.BackboneClient;
import com.umdc.smartkin.client.backbone.to.BackboneEmailLookupRequest;
import com.umdc.smartkin.client.backbone.to.BackboneEmailLookupResponse;
import com.umdc.smartkin.client.backbone.to.BackbonePasswordResetRequest;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.umdc.smartkin.api.v1.service.PasswordRecoveryServiceImpl.CODE_NOT_VALID_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.MESSAGE_ERROR_HEADER;
import static com.umdc.smartkin.support.TestData.APPLICATION_ID;
import static com.umdc.smartkin.support.TestData.USER_ID;
import static com.umdc.smartkin.support.TestData.feign;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceImplTest {

    private static final String EMAIL = "ana@smartkin.test";
    private static final String MERCURY_TOKEN = "mercury-token";

    @Mock
    private BackboneClient backboneClient;
    @Mock
    private MercuryClient mercuryClient;
    @Mock
    private MercuryTokens mercuryTokens;
    @Mock
    private MercuryMailer mercuryMailer;
    @Mock
    private ConfirmCodeMapper confirmCodeMapper;

    private MutableClock clock;
    private PasswordRecoveryServiceImpl service;
    private final ResetPasswordRequest reset = new ResetPasswordRequest(EMAIL, "1234-5678", "N3w$Passw0rd");
    private final VerificationCodeRequest verification = new VerificationCodeRequest(APPLICATION_ID, USER_ID, "1234-5678");

    /// A clock the tests move forward.
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-17T10:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        service = new PasswordRecoveryServiceImpl(backboneClient, mercuryClient, mercuryTokens, mercuryMailer, confirmCodeMapper, clock);
        ReflectionTestUtils.setField(service, "applicationId", APPLICATION_ID);
    }

    @SuppressWarnings("unchecked")
    private void mercuryTokenIsProvided() {
        when(mercuryTokens.withToken(any())).thenAnswer(call -> ((Function<String, Object>) call.getArgument(0)).apply(MERCURY_TOKEN));
    }

    private void theOwnerIs(boolean active) {
        when(backboneClient.lookupByEmail(eq(APPLICATION_ID), any(BackboneEmailLookupRequest.class)))
                .thenReturn(new BackboneEmailLookupResponse(USER_ID, EMAIL, "Ana", active));
    }

    @Test
    void forgotSendsTheCodeToTheOwnerOfAnActiveAccount() {
        theOwnerIs(true);

        assertEquals(HttpStatus.ACCEPTED, service.forgot(new ForgotPasswordRequest("  Ana@SmartKin.test ")).getStatusCode());

        verify(backboneClient).lookupByEmail(APPLICATION_ID, new BackboneEmailLookupRequest(EMAIL));
        verify(mercuryMailer).sendPasswordResetCode(USER_ID, EMAIL, "Ana");
    }

    @Test
    void forgotAnswersTheSameWhenThereIsNoAccountOrItIsInactive() {
        when(backboneClient.lookupByEmail(eq(APPLICATION_ID), any())).thenThrow(feign(404));
        assertEquals(HttpStatus.ACCEPTED, service.forgot(new ForgotPasswordRequest("nobody@smartkin.test")).getStatusCode());

        theOwnerIs(false);
        assertEquals(HttpStatus.ACCEPTED, service.forgot(new ForgotPasswordRequest(EMAIL)).getStatusCode());

        verifyNoInteractions(mercuryMailer);
    }

    @Test
    void forgotStillAnswersAcceptedWhenBackboneOrMercuryFail() {
        when(backboneClient.lookupByEmail(eq(APPLICATION_ID), any())).thenThrow(feign(500));
        assertEquals(HttpStatus.ACCEPTED, service.forgot(new ForgotPasswordRequest("a@smartkin.test")).getStatusCode());

        theOwnerIs(true);
        doThrow(new MailDeliveryException("down")).when(mercuryMailer).sendPasswordResetCode(any(), any(), any());
        assertEquals(HttpStatus.ACCEPTED, service.forgot(new ForgotPasswordRequest("b@smartkin.test")).getStatusCode());
    }

    @Test
    void forgotSendsOneCodePerEmailPerMinute() {
        theOwnerIs(true);

        service.forgot(new ForgotPasswordRequest(EMAIL));
        service.forgot(new ForgotPasswordRequest(EMAIL.toUpperCase()));
        verify(mercuryMailer, org.mockito.Mockito.times(1)).sendPasswordResetCode(USER_ID, EMAIL, "Ana");

        clock.now = clock.now.plusSeconds(61);
        service.forgot(new ForgotPasswordRequest(EMAIL));
        verify(mercuryMailer, org.mockito.Mockito.times(2)).sendPasswordResetCode(USER_ID, EMAIL, "Ana");
    }

    @Test
    void forgotDropsTheEntriesWhoseCooldownIsOverWhenTheTableIsFull() {
        @SuppressWarnings("unchecked")
        var table = (java.util.concurrent.ConcurrentHashMap<String, Instant>) ReflectionTestUtils.getField(service, "lastRequest");
        for (int i = 0; i < 10_000; i++) {
            table.put("old" + i + "@smartkin.test", clock.now.minusSeconds(3600));
        }
        theOwnerIs(true);

        service.forgot(new ForgotPasswordRequest(EMAIL));

        assertEquals(1, table.size());
    }

    @Test
    void resetSetsThePasswordWhenMercuryAcceptsTheCode() {
        theOwnerIs(true);
        mercuryTokenIsProvided();
        when(confirmCodeMapper.toVerificationCodeRequest(any(), eq(APPLICATION_ID))).thenReturn(verification);
        when(mercuryClient.confirmCode(MERCURY_TOKEN, verification)).thenReturn(org.springframework.http.ResponseEntity.accepted().build());

        assertEquals(HttpStatus.NO_CONTENT, service.reset(reset).getStatusCode());

        verify(backboneClient).resetPassword(APPLICATION_ID, USER_ID, new BackbonePasswordResetRequest("N3w$Passw0rd"));
    }

    @Test
    void resetAnswersTheSameNotValidForAnUnknownOrInactiveAccount() {
        when(backboneClient.lookupByEmail(eq(APPLICATION_ID), any())).thenThrow(feign(404));
        var unknown = service.reset(reset);
        theOwnerIs(false);
        var inactive = service.reset(reset);

        for (var response : List.of(unknown, inactive)) {
            assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());
            assertEquals(CODE_NOT_VALID_MESSAGE, response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        }
        verifyNoInteractions(mercuryClient);
        verify(backboneClient, never()).resetPassword(any(), any(), any());
    }

    @Test
    void resetNeverTouchesBackboneWhenTheCodeIsWrongSpentOrExpired() {
        theOwnerIs(true);
        mercuryTokenIsProvided();
        when(confirmCodeMapper.toVerificationCodeRequest(any(), eq(APPLICATION_ID))).thenReturn(verification);

        for (int status : new int[]{406, 404}) {
            doThrow(feign(status, Map.of("Attempts-Remaining", List.of("2")))).when(mercuryClient).confirmCode(MERCURY_TOKEN, verification);
            var response = service.reset(reset);
            assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());
            assertEquals(CODE_NOT_VALID_MESSAGE, response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
            assertNull(response.getHeaders().getFirst("Attempts-Remaining"));
        }
        verify(backboneClient, never()).resetPassword(any(), any(), any());
        verify(mercuryClient, never()).isVerificationCodeDone(any(), any());
    }

    @Test
    void resetReportsAnUnavailableServiceWhenMercuryOrTheLookupFail() {
        theOwnerIs(true);
        mercuryTokenIsProvided();
        when(confirmCodeMapper.toVerificationCodeRequest(any(), eq(APPLICATION_ID))).thenReturn(verification);
        doThrow(feign(500)).when(mercuryClient).confirmCode(MERCURY_TOKEN, verification);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, service.reset(reset).getStatusCode());

        doThrow(feign(403)).when(mercuryClient).confirmCode(MERCURY_TOKEN, verification);
        assertEquals(HttpStatus.BAD_GATEWAY, service.reset(reset).getStatusCode());

        doThrow(feign(503)).when(backboneClient).lookupByEmail(eq(APPLICATION_ID), any());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, service.reset(reset).getStatusCode());
    }

    @Test
    void resetRelaysThePolicyViolationsOfBackbone() {
        theOwnerIs(true);
        mercuryTokenIsProvided();
        when(confirmCodeMapper.toVerificationCodeRequest(any(), eq(APPLICATION_ID))).thenReturn(verification);
        when(backboneClient.resetPassword(eq(APPLICATION_ID), eq(USER_ID), any()))
                .thenThrow(feign(422, Map.of("Warning", List.of("needs a digit"))));

        var response = service.reset(reset);

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertEquals("needs a digit", response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
    }

    @Test
    void resetUsesAGenericMessageWhenBackboneDoesNotListTheViolations() {
        theOwnerIs(true);
        mercuryTokenIsProvided();
        when(confirmCodeMapper.toVerificationCodeRequest(any(), eq(APPLICATION_ID))).thenReturn(verification);
        when(backboneClient.resetPassword(eq(APPLICATION_ID), eq(USER_ID), any())).thenThrow(feign(422));

        assertTrue(service.reset(reset).getHeaders().getFirst(MESSAGE_ERROR_HEADER).contains("security policy"));
    }

    @Test
    void resetReportsBackboneFailuresAfterTheCode() {
        theOwnerIs(true);
        mercuryTokenIsProvided();
        when(confirmCodeMapper.toVerificationCodeRequest(any(), eq(APPLICATION_ID))).thenReturn(verification);
        when(backboneClient.resetPassword(eq(APPLICATION_ID), eq(USER_ID), any())).thenThrow(feign(403));

        assertEquals(HttpStatus.BAD_GATEWAY, service.reset(reset).getStatusCode());
    }

    @Test
    void theRequestsNeverPrintTheCodeOrThePassword() {
        assertFalse(reset.toString().contains("1234-5678"));
        assertFalse(reset.toString().contains("N3w$Passw0rd"));
        assertFalse(new BackbonePasswordResetRequest("N3w$Passw0rd").toString().contains("N3w$Passw0rd"));
    }
}
