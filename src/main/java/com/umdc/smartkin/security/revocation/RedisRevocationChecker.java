package com.umdc.smartkin.security.revocation;

import com.umdc.smartkin.redis.RedisConnector;
import io.lettuce.core.RedisException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/// Looks a token's `jti` up in the deny-list backbone-rest keeps in Redis (`jti:<value>`, expiring with the token).
///
/// The check fails closed: when Redis cannot be reached the answer is a [SessionValidationUnavailableException], never "not revoked".
/// Answers are cached for `cacheTtl` to spare Redis one query per request.
@Component
@EnableConfigurationProperties(RevocationProperties.class)
@ConditionalOnProperty(prefix = "prx.security.revocation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisRevocationChecker implements RevocationChecker {

    static final String KEY_PREFIX = "jti:";
    private static final Logger logger = LoggerFactory.getLogger(RedisRevocationChecker.class);

    private final RedisConnector redis;
    private final Clock clock;
    private final long cacheTtlMillis;
    private final int maxCacheEntries;
    private final ConcurrentMap<String, Answer> cache = new ConcurrentHashMap<>();

    private record Answer(boolean revoked, long expiresAtMillis) {
    }

    @Autowired   // the other constructor exists so tests can inject a Clock
    public RedisRevocationChecker(RevocationProperties properties) {
        this(properties, Clock.systemUTC());
    }

    RedisRevocationChecker(RevocationProperties properties, Clock clock) {
        if (!StringUtils.hasText(properties.redisUrl())) {
            throw new IllegalStateException("prx.security.revocation.redis-url (REVOCATION_REDIS_URL) is required while token revocation "
                    + "checks are enabled. It must point at the Redis backbone-rest uses for its jti deny-list. "
                    + "Set prx.security.revocation.enabled=false (REVOCATION_ENABLED) only for local development.");
        }
        this.redis = new RedisConnector(properties.redisUrl(), properties.commandTimeout());
        this.clock = clock;
        this.cacheTtlMillis = properties.cacheTtl().toMillis();
        this.maxCacheEntries = properties.maxCacheEntries();
    }

    @Override
    public boolean isRevoked(String jti) {
        long now = clock.millis();
        Answer cached = cache.get(jti);
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.revoked();
        }
        boolean revoked = askRedis(jti);
        if (cacheTtlMillis > 0) {
            remember(jti, new Answer(revoked, now + cacheTtlMillis), now);
        }
        return revoked;
    }

    private boolean askRedis(String jti) {
        try {
            return redis.commands().exists(KEY_PREFIX + jti) > 0;
        } catch (RedisException e) {
            logger.error("The token revocation list is unavailable: {}", e.getMessage());
            throw new SessionValidationUnavailableException("Could not consult the token revocation list", e);
        }
    }

    private void remember(String jti, Answer answer, long now) {
        if (cache.size() >= maxCacheEntries) {
            cache.values().removeIf(a -> a.expiresAtMillis() <= now);
            if (cache.size() >= maxCacheEntries) {
                cache.clear();
            }
        }
        cache.put(jti, answer);
    }

    @PreDestroy
    void shutdown() {
        redis.close();
    }
}
