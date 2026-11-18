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
import java.util.Optional;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MercuryMailerTest {

    private static final String TOKEN = "mercury-token";
    private static final UUID TEMPLATE = UUID.randomUUID();
    private static final UUID RESET_TEMPLATE = UUID.randomUUID();

    @Mock
    private MercuryClient mercuryClient;
    @Mock
    private MercuryTokens mercuryTokens;
    @Mock
    private TemplateIdRegistry templates;

    private MercuryMailer mailer;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(Instant.parse("2026-11-15T12:30:00Z"), ZoneOffset.UTC);
        mailer = new MercuryMailer(mercuryClient, mercuryTokens, templates, clock, APPLICATION_ID, "", "es", "support@smartkin.test");
        lenient().when(templates.idOf("verification", "es")).thenReturn(Optional.of(TEMPLATE));
        lenient().when(templates.idOf("password_reset", "es")).thenReturn(Optional.of(RESET_TEMPLATE));
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

    @Test
    void thePasswordResetEmailUsesItsOwnTemplateAndKey() {
        runsCallsWithTheMercuryToken();
        when(mercuryClient.sendMessage(eq(TOKEN), any(), any()))
                .thenReturn(ResponseEntity.accepted().body(new MercuryMessageResponse(UUID.randomUUID(), "OPENED", null)));

        mailer.sendPasswordResetCode(USER_ID, "ana@smartkin.test", "Ana Lopez");

        long minute = Instant.parse("2026-11-15T12:30:00Z").getEpochSecond() / 60;
        var request = ArgumentCaptor.forClass(MercuryMessageRequest.class);
        verify(mercuryClient).sendMessage(eq(TOKEN), eq("RESET-CODE:" + USER_ID + ":" + minute), request.capture());
        assertEquals(RESET_TEMPLATE, request.getValue().templateDefinedId());
        assertEquals("Ana Lopez", request.getValue().params().get("user_name"));
        assertTrue(request.getValue().params().get("vc").toString().matches("\\d{4}-\\d{4}"));
    }

    @Test
    void thePasswordResetEmailFailsWhenMercuryHasNoTemplateForIt() {
        when(templates.idOf("password_reset", "es")).thenReturn(Optional.empty());

        var failure = assertThrows(MailDeliveryException.class, () -> mailer.sendPasswordResetCode(USER_ID, "a@b.c", "Ana"));

        assertTrue(failure.getMessage().contains("sk_password_reset_es"));
        org.mockito.Mockito.verifyNoInteractions(mercuryClient);
    }

    @Test
    void theVerificationEmailFallsBackToTheConfiguredIdWhileMercuryListsNone() {
        var fallback = UUID.randomUUID();
        var withFallback = new MercuryMailer(mercuryClient, mercuryTokens, templates,
                Clock.fixed(Instant.parse("2026-11-15T12:30:00Z"), ZoneOffset.UTC), APPLICATION_ID, " " + fallback + " ", "es", "support@smartkin.test");
        when(templates.idOf("verification", "es")).thenReturn(Optional.empty());
        runsCallsWithTheMercuryToken();
        when(mercuryClient.sendMessage(eq(TOKEN), any(), any()))
                .thenReturn(ResponseEntity.accepted().body(new MercuryMessageResponse(UUID.randomUUID(), "OPENED", null)));

        withFallback.sendVerificationCode(USER_ID, "a@b.c", "Ana");

        var request = ArgumentCaptor.forClass(MercuryMessageRequest.class);
        verify(mercuryClient).sendMessage(eq(TOKEN), any(), request.capture());
        assertEquals(fallback, request.getValue().templateDefinedId());
    }

    @Test
    void theVerificationEmailFailsWithoutATemplateOrFallback() {
        when(templates.idOf("verification", "es")).thenReturn(Optional.empty());

        assertThrows(MailDeliveryException.class, () -> mailer.sendVerificationCode(USER_ID, "a@b.c", "Ana"));
    }
}
