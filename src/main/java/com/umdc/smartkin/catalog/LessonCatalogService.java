package com.umdc.smartkin.catalog;

import com.umdc.smartkin.mongo.document.LessonStatus;
import com.umdc.smartkin.mongo.repository.LessonRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/// Reads the published lessons of a topic, going through the cache first.
@Service
public class LessonCatalogService {

    private final LessonRepository lessons;
    private final LessonCatalogCache cache;

    public LessonCatalogService(LessonRepository lessons, LessonCatalogCache cache) {
        this.lessons = lessons;
        this.cache = cache;
    }

    /// @return the published lessons of the topic, from the cache when present, otherwise from MongoDB (and then cached)
    public List<LessonSummary> publishedLessons(UUID topicId) {
        return cache.get(topicId).orElseGet(() -> {
            List<LessonSummary> fresh = lessons.findByTopicIdAndStatus(topicId, LessonStatus.PUBLISHED).stream()
                    .map(LessonSummary::from)
                    .toList();
            cache.put(topicId, fresh);
            return fresh;
        });
    }

    /// Drops the cached list of a topic. Call it whenever a lesson of the topic is published, changed or archived.
    public void evict(UUID topicId) {
        cache.evict(topicId);
    }
}
