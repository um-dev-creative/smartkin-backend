package com.umdc.smartkin.catalog;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Used when no cache Redis is configured: every read is a miss.
@Component
@ConditionalOnExpression("'${umdc.cache.lesson-catalog.redis-url:}'.isBlank()")
public class NoLessonCatalogCache implements LessonCatalogCache {

    public NoLessonCatalogCache() {
        // Default constructor
    }

    @Override
    public Optional<List<LessonSummary>> get(UUID topicId) {
        return Optional.empty();
    }

    @Override
    public void put(UUID topicId, List<LessonSummary> lessons) {
        // Nothing to store
    }

    @Override
    public void evict(UUID topicId) {
        // Nothing to evict
    }
}
