package com.umdc.smartkin.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/// Settings of the lesson catalog cache (`prx.cache.lesson-catalog.*`).
///
/// @param redisUrl       the cache Redis; blank disables the cache
/// @param ttl            how long a topic's list is kept; also the worst-case staleness when nobody evicts it
/// @param commandTimeout connect and command timeout; short, because a slow cache must not slow the catalog down
@ConfigurationProperties("prx.cache.lesson-catalog")
public record LessonCatalogProperties(
        String redisUrl,
        @DefaultValue("10m") Duration ttl,
        @DefaultValue("500ms") Duration commandTimeout) {
}
