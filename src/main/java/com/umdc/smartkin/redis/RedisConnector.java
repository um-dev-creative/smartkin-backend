package com.umdc.smartkin.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.resource.ClientResources;
import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.util.StringUtils;

import java.time.Duration;

/// A lazily opened, self-healing Lettuce connection to one Redis server.
///
/// Commands are rejected while the server is unreachable (no queueing) and every call fails after `commandTimeout`, so callers decide whether an
/// outage means "deny" (token revocation) or "skip" (cache). The connection is reopened on the next call once the server is back.
public class RedisConnector implements AutoCloseable {

    private final ClientResources resources;
    private final RedisClient client;
    private final Object connectionLock = new Object();
    private StatefulRedisConnection<String, String> connection;

    /// @param redisUrl       `redis[s]://[user:password@]host:port/db`
    /// @param commandTimeout connect and command timeout
    /// @throws IllegalArgumentException when the URL is blank
    public RedisConnector(String redisUrl, Duration commandTimeout) {
        if (!StringUtils.hasText(redisUrl)) {
            throw new IllegalArgumentException("A Redis URL is required");
        }
        RedisURI uri = RedisURI.create(redisUrl.trim());
        uri.setTimeout(commandTimeout);
        this.resources = ClientResources.builder().addressResolverGroup(DefaultAddressResolverGroup.INSTANCE).build();
        this.client = RedisClient.create(resources, uri);
        this.client.setOptions(ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .socketOptions(SocketOptions.builder().connectTimeout(commandTimeout).build())
                .timeoutOptions(TimeoutOptions.enabled(commandTimeout))
                .build());
    }

    /// Returns the synchronous commands of an open connection, connecting first when needed.
    ///
    /// @throws io.lettuce.core.RedisException when the server cannot be reached
    public RedisCommands<String, String> commands() {
        synchronized (connectionLock) {
            if (connection == null || !connection.isOpen()) {
                connection = client.connect();
            }
            return connection.sync();
        }
    }

    @Override
    public void close() {
        client.shutdown();
        resources.shutdown();
    }
}
