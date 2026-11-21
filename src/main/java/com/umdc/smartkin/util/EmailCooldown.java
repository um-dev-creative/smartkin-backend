package com.umdc.smartkin.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/// Lets one action through per e-mail per cooldown, so an endpoint cannot be used to flood an inbox. It lives in memory and is bounded: when the
/// table is full the entries whose cooldown is over are dropped. With several instances the limit is per instance; rate limiting by IP belongs
/// to the proxy.
public final class EmailCooldown {

    private static final int MAX_TRACKED = 10_000;

    private final Clock clock;
    private final Duration cooldown;
    private final ConcurrentHashMap<String, Instant> last = new ConcurrentHashMap<>();

    /// @param clock    the clock the cooldown is measured with
    /// @param cooldown the time that must pass before the same e-mail is let through again
    public EmailCooldown(Clock clock, Duration cooldown) {
        this.clock = clock;
        this.cooldown = cooldown;
    }

    /// @param email the e-mail, already normalized (trimmed, lower case)
    /// @return `true` when the caller may act now (and the turn is taken), `false` while the cooldown of this e-mail runs
    public boolean takeTurn(String email) {
        var now = clock.instant();
        if (last.size() >= MAX_TRACKED) {
            last.values().removeIf(at -> at.plus(cooldown).isBefore(now));
        }
        var taken = new boolean[1];
        last.compute(email, (key, previous) -> {
            taken[0] = previous == null || !previous.plus(cooldown).isAfter(now);
            return taken[0] ? now : previous;
        });
        return taken[0];
    }
}
