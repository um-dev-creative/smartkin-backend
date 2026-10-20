package com.umdc.smartkin.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// A cache of the published lessons of a topic. It is an optimisation only: any failure behaves as a miss, never as an error.
public interface LessonCatalogCache {

    /// @return the cached list, or empty on a miss, an expired entry, an unreadable entry or when the cache is unavailable
    Optional<List<LessonSummary>> get(UUID topicId);

    void put(UUID topicId, List<LessonSummary> lessons);

    void evict(UUID topicId);
}
