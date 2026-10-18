package com.umdc.smartkin.catalog;

import com.umdc.smartkin.redis.RedisConnector;
import io.lettuce.core.RedisException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Keeps each topic's published lessons in Redis as JSON under `smartkin:catalog:topic:<topicId>`, expiring after the configured TTL.
///
/// The cache fails open: when Redis is down, slow or holds something unreadable, [#get] answers "miss" and writes are skipped, so the
/// catalog keeps working from MongoDB.
@Component
@EnableConfigurationProperties(LessonCatalogProperties.class)
@ConditionalOnExpression("!'${prx.cache.lesson-catalog.redis-url:}'.isBlank()")
public class RedisLessonCatalogCache implements LessonCatalogCache {

    static final String KEY_PREFIX = "smartkin:catalog:topic:";
    private static final Logger logger = LoggerFactory.getLogger(RedisLessonCatalogCache.class);
    private static final TypeReference<List<LessonSummary>> LESSON_LIST = new TypeReference<>() {
    };

    private final RedisConnector redis;
    private final ObjectMapper mapper;
    private final long ttlSeconds;

    public RedisLessonCatalogCache(LessonCatalogProperties properties, ObjectMapper mapper) {
        this.redis = new RedisConnector(properties.redisUrl(), properties.commandTimeout());
        this.mapper = mapper;
        this.ttlSeconds = Math.max(1, properties.ttl().toSeconds());
    }

    @Override
    public Optional<List<LessonSummary>> get(UUID topicId) {
        try {
            String json = redis.commands().get(key(topicId));
            return json == null ? Optional.empty() : Optional.of(mapper.readValue(json, LESSON_LIST));
        } catch (RedisException | JacksonException e) {
            logger.warn("Lesson catalog cache read skipped for topic {}: {}", topicId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(UUID topicId, List<LessonSummary> lessons) {
        try {
            redis.commands().setex(key(topicId), ttlSeconds, mapper.writeValueAsString(lessons));
        } catch (RedisException | JacksonException e) {
            logger.warn("Lesson catalog cache write skipped for topic {}: {}", topicId, e.getMessage());
        }
    }

    @Override
    public void evict(UUID topicId) {
        try {
            redis.commands().del(key(topicId));
        } catch (RedisException e) {
            logger.warn("Lesson catalog cache eviction failed for topic {}; the entry expires in at most {} s: {}",
                    topicId, ttlSeconds, e.getMessage());
        }
    }

    private static String key(UUID topicId) {
        return KEY_PREFIX + topicId;
    }

    @PreDestroy
    void shutdown() {
        redis.close();
    }
}
