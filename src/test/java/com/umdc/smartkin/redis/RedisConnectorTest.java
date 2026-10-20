package com.umdc.smartkin.redis;

import io.lettuce.core.KillArgs;
import io.lettuce.core.RedisException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class RedisConnectorTest {

    @Container
    static GenericContainer<?> redis = RedisTestSupport.newRedis();

    @Test
    void readsAndWritesThroughTheCommands() {
        try (var connector = new RedisConnector(RedisTestSupport.url(redis), Duration.ofSeconds(2))) {
            connector.commands().set("k", "v");

            assertEquals("v", connector.commands().get("k"));
        }
    }

    @Test
    void reconnectsAfterTheServerClosedTheConnection() throws InterruptedException {
        try (var connector = new RedisConnector(RedisTestSupport.url(redis), Duration.ofSeconds(2))) {
            connector.commands().set("k2", "v2");
            connector.commands().clientKill(KillArgs.Builder.typeNormal().skipme(false));   // drops every client, including this one

            // Lettuce reconnects on its own; commands are rejected (not queued) until it has, so poll briefly.
            String value = null;
            for (int attempt = 0; attempt < 50 && value == null; attempt++) {
                try {
                    value = connector.commands().get("k2");
                } catch (RedisException reconnecting) {
                    Thread.sleep(100);
                }
            }
            assertEquals("v2", value);

        }
    }

    @Test
    void opensANewConnectionWhenTheCurrentOneWasClosed() {
        try (var connector = new RedisConnector(RedisTestSupport.url(redis), Duration.ofSeconds(2))) {
            connector.commands().set("k3", "v3");
            connector.commands().getStatefulConnection().close();

            assertEquals("v3", connector.commands().get("k3"));
        }
    }

    @Test
    void failsFastWhenTheServerIsDown() {
        try (var connector = new RedisConnector("redis://127.0.0.1:1", Duration.ofMillis(500))) {
            assertThrows(RedisException.class, connector::commands);
        }
    }

    @Test
    void rejectsABlankUrl() {
        assertThrows(IllegalArgumentException.class, () -> new RedisConnector(" ", Duration.ofSeconds(1)));
    }
}
