package com.umdc.smartkin.catalog;

import com.umdc.smartkin.mongo.document.LessonDocument;
import com.umdc.smartkin.mongo.document.LessonStatus;
import com.umdc.smartkin.mongo.repository.LessonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonCatalogServiceTest {

    private final UUID topic = UUID.randomUUID();

    @Mock
    private LessonRepository lessons;
    @Mock
    private LessonCatalogCache cache;

    private LessonDocument lesson(String slug) {
        return new LessonDocument(UUID.randomUUID(), slug, topic, "Screen time", "Balance", "es", LessonStatus.PUBLISHED, 2,
                List.of(), null, UUID.randomUUID(), Instant.now(), Instant.now());
    }

    @Test
    void answersFromTheCacheWithoutTouchingMongo() {
        var cached = List.of(LessonSummary.from(lesson("screen-time")));
        when(cache.get(topic)).thenReturn(Optional.of(cached));

        assertEquals(cached, new LessonCatalogService(lessons, cache).publishedLessons(topic));
        verifyNoInteractions(lessons);
    }

    @Test
    void readsMongoOnAMissAndCachesTheSummaries() {
        var stored = lesson("screen-time");
        when(cache.get(topic)).thenReturn(Optional.empty());
        when(lessons.findByTopicIdAndStatus(topic, LessonStatus.PUBLISHED)).thenReturn(List.of(stored));

        var result = new LessonCatalogService(lessons, cache).publishedLessons(topic);

        assertEquals(List.of(new LessonSummary(stored.id(), "screen-time", topic, "Screen time", "Balance", "es", 2)), result);
        verify(cache).put(topic, result);
    }

    @Test
    void cachesAnEmptyCatalogToo() {
        when(cache.get(topic)).thenReturn(Optional.empty());
        when(lessons.findByTopicIdAndStatus(topic, LessonStatus.PUBLISHED)).thenReturn(List.of());

        assertEquals(List.of(), new LessonCatalogService(lessons, cache).publishedLessons(topic));
        verify(cache).put(topic, List.of());
    }

    @Test
    void evictsTheTopicFromTheCache() {
        new LessonCatalogService(lessons, cache).evict(topic);

        verify(cache).evict(topic);
    }

    @Test
    void theNoopCacheAlwaysMisses() {
        var noop = new NoLessonCatalogCache();

        noop.put(topic, List.of());
        noop.evict(topic);

        assertEquals(Optional.empty(), noop.get(topic));
    }
}
