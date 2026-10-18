package com.umdc.smartkin.security.revocation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevocationWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RedisRevocationChecker.class, NoRevocationChecker.class);

    @Test
    void usesTheNoopCheckerWhenRevocationIsDisabled() {
        runner.withPropertyValues("prx.security.revocation.enabled=false").run(context -> {
            assertTrue(context.getBean(RevocationChecker.class) instanceof NoRevocationChecker);
            assertFalse(context.getBean(RevocationChecker.class).isRevoked("any"));
        });
    }

    @Test
    void usesTheRedisCheckerByDefault() {
        runner.withPropertyValues("prx.security.revocation.redis-url=redis://127.0.0.1:6379").run(context ->
                assertTrue(context.getBean(RevocationChecker.class) instanceof RedisRevocationChecker));
    }

    @Test
    void failsToStartWhenRevocationIsOnAndNoRedisIsConfigured() {
        runner.run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void answers503WhenTheRevocationListIsUnavailable() {
        var response = new SessionValidationExceptionHandler()
                .unavailable(new SessionValidationUnavailableException("down", new IllegalStateException()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("Session validation is temporarily unavailable.", response.getHeaders().getFirst("message-error"));
    }
}
