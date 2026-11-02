package com.example.config;

import java.time.Duration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/** Optional manager for expensive read-only loads, not a business-operation lock. */
public final class CoalescingRedisCacheManager {
    private CoalescingRedisCacheManager() {}

    public static RedisCacheManager create(RedisConnectionFactory factory,
            RedisCacheConfiguration configuration, Duration lockTtl) {
        if (lockTtl == null || lockTtl.isNegative() || lockTtl.isZero()) {
            throw new IllegalArgumentException("A positive lock TTL is required");
        }
        var writer = RedisCacheWriter.create(factory, settings -> settings.immediateWrites()
            .batchStrategy(BatchStrategies.scan(1000))
            .enableLocking(lock -> lock.sleepTime(Duration.ofMillis(25))
                .lockTimeout(RedisCacheWriter.TtlFunction.just(lockTtl))));
        return RedisCacheManager.builder(writer).cacheDefaults(configuration).build();
    }
}
