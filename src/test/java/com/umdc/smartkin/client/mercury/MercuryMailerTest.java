package com.umdc.smartkin.client.mercury;

import com.umdc.smartkin.client.mercury.to.MercuryMessageRequest;
import com.umdc.smartkin.client.mercury.to.MercuryMessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Function;

import static com.umdc.smartkin.support.TestData.APPLICATION_ID;
import static com.umdc.smartkin.support.TestData.USER_ID;
import static com.umdc.smartkin.support.TestData.feign;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MercuryMailerTest {

    private static final String TOKEN = "mercury-token";
    private static final UUID TEMPLATE = UUID.randomUUID();

    @Mock
    private MercuryClient mercuryClient;
    @Mock
    private MercuryTokens mercuryTokens;

    private MercuryMailer mailer;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(Instant.parse("2026-11-15T12:30:00Z"), ZoneOffset.UTC);
        mailer = new MercuryMailer(mercuryClient, mercuryTokens, clock, APPLICATION_ID, TEMPLATE, "es", "support@smartkin.test");
    }

    @SuppressWarnings("unchecked")
    private void runsCallsWithTheMercuryToken() {
        when(mercuryTokens.withToken(any())).thenAnswer(call -> ((Function<String, Object>) call.getArgument(0)).apply(TOKEN));
    }

    @Test
    void handsTheVerificationEmailToMercuryWithAnIdempotencyKey() {
        runsCallsWithTheMercuryToken();
        when(mercuryClient.sendMessage(eq(TOKEN), any(), any()))
                .thenReturn(ResponseEntity.accepted().body(new MercuryMessageResponse(UUID.randomUUID(), "OPENED", null)));

        mailer.sendVerificationCode(USER_ID, "ana@smartkin.test", "Ana Lopez");

        var request = ArgumentCaptor.forClass(MercuryMessageRequest.class);
        verify(mercuryClient).sendMessage(eq(TOKEN), eq("REGISTER-CODE:" + USER_ID), request.capture());
        var sent = request.getValue();
        assertEquals("email", sent.channel());
        assertEquals(USER_ID, sent.userId());
        assertEquals("ana@smartkin.test", sent.target());
        assertEquals(TEMPLATE, sent.templateDefinedId());
        assertEquals(APPLICATION_ID, sent.applicationId());
        assertEquals("es", sent.language());
        assertEquals("support@smartkin.test", sent.from());
        assertEquals("2026-11-15T12:30", sent.sendDate().toString());
        assertEquals("Ana Lopez", sent.params().get("user_name"));
        assertTrue(sent.params().get("vc").toString().matches("\\d{4}-\\d{4}"));
        assertFalse(sent.params().containsKey("subject"));
    }

    @Test
    void aResendUsesItsOwnIdempotencyKeyPerMinute() {
        runsCallsWithTheMercuryToken();
        when(mercuryClient.sendMessage(eq(TOKEN), any(), any()))
                .thenReturn(ResponseEntity.accepted().body(new MercuryMessageResponse(UUID.randomUUID(), "OPENED", null)));

        mailer.resendVerificationCode(USER_ID, "ana@smartkin.test", "Ana Lopez");

        long minute = Instant.parse("2026-11-15T12:30:00Z").getEpochSecond() / 60;
        verify(mercuryClient).sendMessage(eq(TOKEN), eq("RESEND-CODE:" + USER_ID + ":" + minute), any());
    }

    @Test
    void failsWhenMercuryAnswersAnError() {
        runsCallsWithTheMercuryToken();
        when(mercuryClient.sendMessage(any(), any(), any())).thenThrow(feign(400));

        var failure = assertThrows(MailDeliveryException.class, () -> mailer.sendVerificationCode(USER_ID, "a@b.c", "Ana"));

        assertTrue(failure.getMessage().contains("400"));
        assertFalse(failure.getMessage().contains("a@b.c"));
    }

    @Test
    void failsWhenMercuryAnswersANonSuccessStatus() {
        runsCallsWithTheMercuryToken();
        when(mercuryClient.sendMessage(any(), any(), any())).thenReturn(ResponseEntity.status(302).build());

        assertThrows(MailDeliveryException.class, () -> mailer.sendVerificationCode(USER_ID, "a@b.c", "Ana"));
    }

    @Test
    void failsWhenTheManagedClientIsNotConfigured() {
        when(mercuryTokens.withToken(any())).thenThrow(new IllegalStateException("MERCURY_M2M_CLIENT_ID missing"));

        var failure = assertThrows(MailDeliveryException.class, () -> mailer.sendVerificationCode(USER_ID, "a@b.c", "Ana"));

        assertTrue(failure.getMessage().contains("not configured"));
    }

    @Test
    void theExceptionKeepsItsCause() {
        var cause = new RuntimeException("boom");
        assertEquals(cause, new MailDeliveryException("x", cause).getCause());
    }
}
