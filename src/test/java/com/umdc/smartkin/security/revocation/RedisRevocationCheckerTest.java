package com.umdc.smartkin.security.revocation;

import com.umdc.smartkin.redis.RedisConnector;
import com.umdc.smartkin.redis.RedisTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class RedisRevocationCheckerTest {

    static GenericContainer<?> redis = RedisTestSupport.newRedis();
    private static RedisConnector admin;

    @BeforeAll
    static void startRedis() {
        redis.start();
        admin = new RedisConnector(RedisTestSupport.url(redis), Duration.ofSeconds(2));
    }

    @AfterAll
    static void stopRedis() {
        admin.close();
        redis.stop();
    }

    /// A clock the test can move forward.
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-18T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

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

    private RedisRevocationChecker checker(String url, Duration cacheTtl, int maxEntries, Clock clock) {
        return new RedisRevocationChecker(new RevocationProperties(true, url, cacheTtl, Duration.ofSeconds(1), maxEntries), clock);
    }

    private RedisRevocationChecker checker(Duration cacheTtl) {
        return checker(RedisTestSupport.url(redis), cacheTtl, 100, Clock.systemUTC());
    }

    private static void revoke(String jti) {
        admin.commands().set("jti:" + jti, "1");
    }

    @Test
    void reportsATokenOnTheDenyListAsRevoked() {
        var jti = UUID.randomUUID().toString();
        revoke(jti);
        var checker = checker(Duration.ZERO);

        assertTrue(checker.isRevoked(jti));
        checker.shutdown();
    }

    @Test
    void reportsAnUnknownTokenAsNotRevoked() {
        var checker = checker(Duration.ZERO);

        assertFalse(checker.isRevoked(UUID.randomUUID().toString()));
        checker.shutdown();
    }

    @Test
    void remembersAnAnswerUntilTheCacheTtlPasses() {
        var clock = new MovableClock();
        var checker = checker(RedisTestSupport.url(redis), Duration.ofSeconds(30), 100, clock);
        var jti = UUID.randomUUID().toString();

        assertFalse(checker.isRevoked(jti));
        revoke(jti);
        assertFalse(checker.isRevoked(jti));
        clock.advance(Duration.ofSeconds(31));
        assertTrue(checker.isRevoked(jti));
        checker.shutdown();
    }

    @Test
    void asksRedisEveryTimeWhenTheCacheIsOff() {
        var checker = checker(Duration.ZERO);
        var jti = UUID.randomUUID().toString();

        assertFalse(checker.isRevoked(jti));
        revoke(jti);
        assertTrue(checker.isRevoked(jti));
        checker.shutdown();
    }

    @Test
    void dropsOldAnswersWhenTheCacheIsFull() {
        var clock = new MovableClock();
        var checker = checker(RedisTestSupport.url(redis), Duration.ofSeconds(30), 2, clock);
        var first = UUID.randomUUID().toString();

        assertFalse(checker.isRevoked(first));
        assertFalse(checker.isRevoked(UUID.randomUUID().toString()));
        clock.advance(Duration.ofSeconds(31));      // both answers are now stale: pruned when the next one is stored
        assertFalse(checker.isRevoked(UUID.randomUUID().toString()));
        assertFalse(checker.isRevoked(UUID.randomUUID().toString()));
        assertFalse(checker.isRevoked(UUID.randomUUID().toString()));   // full of fresh answers: cleared
        revoke(first);
        assertTrue(checker.isRevoked(first));
        checker.shutdown();
    }

    @Test
    void failsClosedWhenRedisCannotBeReached() {
        var checker = checker("redis://127.0.0.1:1", Duration.ZERO, 100, Clock.systemUTC());

        assertThrows(SessionValidationUnavailableException.class, () -> checker.isRevoked(UUID.randomUUID().toString()));
        checker.shutdown();
    }

    @Test
    void refusesToStartWithoutARedisUrl() {
        var properties = new RevocationProperties(true, " ", Duration.ofSeconds(30), Duration.ofSeconds(2), 10);

        var failure = assertThrows(IllegalStateException.class, () -> new RedisRevocationChecker(properties));

        assertTrue(failure.getMessage().contains("REVOCATION_REDIS_URL"));
    }
}
