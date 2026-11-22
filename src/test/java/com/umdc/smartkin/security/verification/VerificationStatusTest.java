package com.umdc.smartkin.security.verification;

import com.umdc.smartkin.client.mercury.MercuryClient;
import com.umdc.smartkin.client.mercury.MercuryTokens;
import com.umdc.smartkin.security.revocation.SessionValidationUnavailableException;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationStatusTest {

    private final MercuryClient mercury = mock(MercuryClient.class);
    private final MercuryTokens tokens = mock(MercuryTokens.class);
    private final VerificationStatus status = new VerificationStatus(mercury, tokens);
    private final UUID user = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void runsCallsWithAToken() {
        when(tokens.withToken(any(Function.class))).thenAnswer(call -> ((Function<String, Object>) call.getArgument(0)).apply("m2m"));
    }

    private static FeignException status(int code) {
        var request = Request.create(Request.HttpMethod.GET, "/x", Map.of(), new byte[0], StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("isVerificationCodeDone",
                feign.Response.builder().status(code).reason("r").request(request).headers(Map.of()).build());
    }

    @Test
    void aConfirmedCodeIsRememberedSoMercuryIsAskedOnce() {
        when(mercury.isVerificationCodeDone("m2m", user)).thenReturn(true);

        assertTrue(status.isVerified(user));
        assertTrue(status.isVerified(user));
        verify(mercury, times(1)).isVerificationCodeDone("m2m", user);
    }

    @Test
    void aPendingCodeIsAskedAgainEveryTime() {
        when(mercury.isVerificationCodeDone("m2m", user)).thenReturn(false, null, true);

        assertFalse(status.isVerified(user));
        assertFalse(status.isVerified(user));
        assertTrue(status.isVerified(user));
    }

    @Test
    void noCodeInMercuryIsNotVerified() {
        when(mercury.isVerificationCodeDone("m2m", user)).thenThrow(status(404));

        assertFalse(status.isVerified(user));
    }

    @Test
    void mercuryFailingOrWithoutTokenIsNotAnAnswer() {
        when(mercury.isVerificationCodeDone("m2m", user)).thenThrow(status(503));
        assertThrows(SessionValidationUnavailableException.class, () -> status.isVerified(user));

        var other = UUID.randomUUID();
        when(mercury.isVerificationCodeDone("m2m", other)).thenThrow(new IllegalStateException("no M2M client"));
        assertThrows(SessionValidationUnavailableException.class, () -> status.isVerified(other));
    }
}
