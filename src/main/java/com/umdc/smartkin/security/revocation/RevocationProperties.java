package com.umdc.smartkin.security.revocation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/// Settings of the token revocation check (`umdc.security.revocation.*`).
///
/// @param enabled         `false` skips the check; revoked tokens then stay valid until they expire (local development only)
/// @param redisUrl        the Redis backbone-rest writes its `jti:` deny-list to
/// @param cacheTtl        how long an answer is remembered; also the worst-case delay before a revocation takes effect
/// @param commandTimeout  connect and command timeout against Redis
/// @param maxCacheEntries upper bound of remembered answers
@ConfigurationProperties("umdc.security.revocation")
public record RevocationProperties(
        @DefaultValue("true") boolean enabled,
        String redisUrl,
        @DefaultValue("30s") Duration cacheTtl,
        @DefaultValue("2s") Duration commandTimeout,
        @DefaultValue("10000") int maxCacheEntries) {
}
