package com.umdc.smartkin.security.verification;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifiedSessionInterceptorTest {

    private static final String API = "/api/v1";
    private final VerifiedSessionInterceptor interceptor = new VerifiedSessionInterceptor(API, true);

    private static String token(String claims) {
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
    void aVerifiedSessionReachesTheApi() {
        assertTrue(allows("GET", API + "/children", token("{\"vcCompleted\":\"true\"}")));
    }

    @Test
    void anUnverifiedSessionIsRefusedWithTheReason() {
        var response = new MockHttpServletResponse();

        assertFalse(allows("GET", API + "/children", token("{\"vcCompleted\":\"false\"}"), response));
        assertEquals(403, response.getStatus());
        assertEquals("Verify your e-mail to use SmartKin.", response.getHeader("message-error"));
        assertFalse(allows("POST", API + "/assignments", token("{\"uid\":\"x\"}")));
        assertFalse(allows("GET", API + "/children", null));
    }

    @Test
    void anUnverifiedSessionStillGetsVerified() {
        var unverified = token("{\"vcCompleted\":\"false\"}");

        assertTrue(allows("POST", API + "/users", unverified));
        assertTrue(allows("POST", API + "/user-register", unverified));
        assertTrue(allows("POST", API + "/user-register/abc/resend", unverified));
        assertTrue(allows("GET", API + "/auth/session-token", unverified));
        assertTrue(allows("GET", API + "/users/abc", unverified));
        assertTrue(allows("OPTIONS", API + "/children", unverified));
    }

    @Test
    void anUnverifiedSessionCannotChangeOrDeleteAnAccount() {
        var unverified = token("{\"vcCompleted\":\"false\"}");

        assertFalse(allows("PUT", API + "/users/abc", unverified));
        assertFalse(allows("DELETE", API + "/users/abc", unverified));
    }

    @Test
    void theCheckCanBeSwitchedOff() {
        var off = new VerifiedSessionInterceptor(API, false);
        var request = new MockHttpServletRequest("GET", API + "/children");
        request.setRequestURI(API + "/children");

        assertTrue(off.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void theConfigRegistersItOnTheWholeApi() {
        var registry = new InterceptorRegistry() {
            int count() {
                return getInterceptors().size();
            }
        };
        new VerificationWebConfig(API, true).addInterceptors(registry);

        assertEquals(1, registry.count());
    }
}
