package com.umdc.smartkin.security.verification;

import com.umdc.smartkin.security.revocation.SessionValidationUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// The session is backbone's token: the interceptor reads its `uid` and asks [VerificationStatus] (mercury) whether the user verified.
class VerifiedSessionInterceptorTest {

    private static final String API = "/api/v1";
    private static final UUID VERIFIED = UUID.randomUUID();
    private static final UUID PENDING = UUID.randomUUID();

    private final VerificationStatus status = mock(VerificationStatus.class);
    private final VerifiedSessionInterceptor interceptor = new VerifiedSessionInterceptor(API, true, status);

    {
        when(status.isVerified(VERIFIED)).thenReturn(true);
        when(status.isVerified(PENDING)).thenReturn(false);
    }

    private static String token(UUID uid) {
        var claims = uid == null ? "{\"sub\":\"x\"}" : "{\"uid\":\"" + uid + "\"}";
        return "h." + Base64.getUrlEncoder().withoutPadding().encodeToString(claims.getBytes(StandardCharsets.UTF_8)) + ".s";
    }

    private boolean allows(String method, String path, String token, MockHttpServletResponse response) {
        var request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        if (token != null) {
            request.addHeader("session-token", token);
        }
        return interceptor.preHandle(request, response, new Object());
    }

    private boolean allows(String method, String path, String token) {
        return allows(method, path, token, new MockHttpServletResponse());
    }

    @Test
    void aVerifiedUserReachesTheApi() {
        assertTrue(allows("GET", API + "/children", token(VERIFIED)));
    }

    @Test
    void anUnverifiedUserIsRefusedWithTheReason() {
        var response = new MockHttpServletResponse();

        assertFalse(allows("GET", API + "/children", token(PENDING), response));
        assertEquals(403, response.getStatus());
        assertEquals("Verify your e-mail to use SmartKin.", response.getHeader("message-error"));
    }

    @Test
    void aTokenWithoutUserOrNoTokenIsRefusedWithoutAskingMercury() {
        assertFalse(allows("POST", API + "/assignments", token(null)));
        assertFalse(allows("GET", API + "/children", null));
        verify(status, never()).isVerified(any());
    }

    @Test
    void anUnverifiedUserStillGetsVerified() {
        var unverified = token(PENDING);

        assertTrue(allows("POST", API + "/users", unverified));
        assertTrue(allows("POST", API + "/user-register", unverified));
        assertTrue(allows("POST", API + "/user-register/abc/resend", unverified));
        assertTrue(allows("GET", API + "/user-register/status", unverified));
        assertTrue(allows("GET", API + "/users/abc", unverified));
        assertTrue(allows("OPTIONS", API + "/children", unverified));
    }

    @Test
    void anUnverifiedUserCannotChangeOrDeleteAnAccount() {
        var unverified = token(PENDING);

        assertFalse(allows("PUT", API + "/users/abc", unverified));
        assertFalse(allows("DELETE", API + "/users/abc", unverified));
    }

    @Test
    void whenMercuryCannotSayTheRequestIsNotLetIn() {
        var down = UUID.randomUUID();
        when(status.isVerified(down)).thenThrow(new SessionValidationUnavailableException("mercury down", null));

        assertThrows(SessionValidationUnavailableException.class, () -> allows("GET", API + "/children", token(down)));
    }

    @Test
    void theCheckCanBeSwitchedOff() {
        var off = new VerifiedSessionInterceptor(API, false, status);
        var request = new MockHttpServletRequest("GET", API + "/children");
        request.setRequestURI(API + "/children");

        assertTrue(off.preHandle(request, new MockHttpServletResponse(), new Object()));
        verify(status, never()).isVerified(any());
    }

    @Test
    void theConfigRegistersItOnTheWholeApi() {
        var registry = new InterceptorRegistry() {
            int count() {
                return getInterceptors().size();
            }
        };
        new VerificationWebConfig(API, true, status).addInterceptors(registry);

        assertEquals(1, registry.count());
    }
}
