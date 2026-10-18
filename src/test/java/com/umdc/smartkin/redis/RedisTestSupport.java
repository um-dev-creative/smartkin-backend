package com.umdc.smartkin.redis;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/// Shared Redis container settings for the integration tests.
public final class RedisTestSupport {

    private RedisTestSupport() {
    }

    public static GenericContainer<?> newRedis() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    }

    public static String url(GenericContainer<?> redis) {
        return "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
    }
}
