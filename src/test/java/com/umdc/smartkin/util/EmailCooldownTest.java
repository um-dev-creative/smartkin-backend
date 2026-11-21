package com.umdc.smartkin.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailCooldownTest {

    private Instant now = Instant.parse("2026-11-21T22:00:00Z");
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };

    @Test
    void letsOneThroughPerEmailPerCooldown() {
        var cooldown = new EmailCooldown(clock, Duration.ofMinutes(1));

        assertTrue(cooldown.takeTurn("a@b.co"));
        assertFalse(cooldown.takeTurn("a@b.co"));
        assertTrue(cooldown.takeTurn("c@d.co"));

        now = now.plusSeconds(59);
        assertFalse(cooldown.takeTurn("a@b.co"));
        now = now.plusSeconds(1);
        assertTrue(cooldown.takeTurn("a@b.co"));
    }

    @Test
    void dropsTheExpiredEntriesWhenTheTableIsFull() {
        var cooldown = new EmailCooldown(clock, Duration.ofMinutes(1));
        for (int i = 0; i < 10_000; i++) {
            cooldown.takeTurn("user" + i + "@b.co");
        }

        now = now.plus(Duration.ofMinutes(5));

        assertTrue(cooldown.takeTurn("late@b.co"));
        assertTrue(cooldown.takeTurn("user0@b.co"));
    }
}
