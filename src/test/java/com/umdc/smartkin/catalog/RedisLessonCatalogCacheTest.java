package com.umdc.smartkin.catalog;

import com.umdc.smartkin.redis.RedisConnector;
import com.umdc.smartkin.redis.RedisTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class RedisLessonCatalogCacheTest {

    static GenericContainer<?> redis = RedisTestSupport.newRedis();
    private static RedisConnector admin;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final UUID topic = UUID.randomUUID();
    private final List<LessonSummary> lessons = List.of(
            new LessonSummary(UUID.randomUUID(), "screen-time", UUID.randomUUID(), "Screen time", "Balance", "es", 1),
            new LessonSummary(UUID.randomUUID(), "privacy", UUID.randomUUID(), "Privacy", "Your data", "es", 3));

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

    private RedisLessonCatalogCache cache(String url, Duration ttl) {
        return new RedisLessonCatalogCache(new LessonCatalogProperties(url, ttl, Duration.ofMillis(500)), mapper);
    }

    private RedisLessonCatalogCache cache() {
        return cache(RedisTestSupport.url(redis), Duration.ofMinutes(10));
    }

    @Test
    void storesAndReadsBackTheLessonsOfATopic() {
        var cache = cache();

        cache.put(topic, lessons);

        assertEquals(Optional.of(lessons), cache.get(topic));
        cache.shutdown();
    }

    @Test
    void answersAMissForAnUnknownTopic() {
        var cache = cache();

        assertEquals(Optional.empty(), cache.get(UUID.randomUUID()));
        cache.shutdown();
    }

    @Test
    void entriesExpireAfterTheConfiguredTtl() {
        var cache = cache(RedisTestSupport.url(redis), Duration.ofMinutes(5));

        cache.put(topic, lessons);

        long ttl = admin.commands().ttl(RedisLessonCatalogCache.KEY_PREFIX + topic);
        assertTrue(ttl > 0 && ttl <= 300, "ttl was " + ttl);
        cache.shutdown();
    }

    @Test
    void evictionRemovesTheEntry() {
        var cache = cache();
        cache.put(topic, lessons);

        cache.evict(topic);

        assertEquals(Optional.empty(), cache.get(topic));
        assertNull(admin.commands().get(RedisLessonCatalogCache.KEY_PREFIX + topic));
        cache.shutdown();
    }

    @Test
    void treatsUnreadableEntriesAsAMiss() {
        var cache = cache();
        admin.commands().set(RedisLessonCatalogCache.KEY_PREFIX + topic, "not json");

        assertEquals(Optional.empty(), cache.get(topic));
        cache.shutdown();
    }

    @Test
    void failsOpenWhenRedisIsDown() {
        var cache = cache("redis://127.0.0.1:1", Duration.ofMinutes(10));

        assertEquals(Optional.empty(), cache.get(topic));
        cache.put(topic, lessons);
        cache.evict(topic);
        cache.shutdown();
    }

    @Test
    void keepsAtLeastOneSecondOfTtl() {
        var cache = cache(RedisTestSupport.url(redis), Duration.ofMillis(10));

        cache.put(topic, lessons);

        assertTrue(admin.commands().ttl(RedisLessonCatalogCache.KEY_PREFIX + topic) >= 0);
        cache.shutdown();
    }

    @Test
    void theCacheBeanDependsOnTheConfiguredRedisUrl() {
        var runner = new ApplicationContextRunner()
                .withBean(JsonMapper.class, () -> mapper)
                .withUserConfiguration(RedisLessonCatalogCache.class, NoLessonCatalogCache.class);

        runner.run(context -> assertTrue(context.getBean(LessonCatalogCache.class) instanceof NoLessonCatalogCache));
        runner.withPropertyValues("umdc.cache.lesson-catalog.redis-url=" + RedisTestSupport.url(redis))
                .run(context -> {
                    assertTrue(context.getBean(LessonCatalogCache.class) instanceof RedisLessonCatalogCache);
                    assertNotNull(context.getBean(LessonCatalogProperties.class));
                });
    }
}
