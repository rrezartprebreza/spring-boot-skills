package com.example.redis;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class AtomicWindowLimiter {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
        local count = redis.call('GET', KEYS[1])
        if not count then
            redis.call('SET', KEYS[1], '1', 'PX', ARGV[2])
            return 1
        end
        if tonumber(count) >= tonumber(ARGV[1]) then return 0 end
        redis.call('INCR', KEYS[1])
        return 1
        """, Long.class);

    private final StringRedisTemplate redis;

    public AtomicWindowLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isAllowed(String scopedBucket, int limit, Duration window) {
        if (scopedBucket == null || scopedBucket.isBlank() || scopedBucket.length() > 256) {
            throw new IllegalArgumentException("A bounded, server-scoped bucket is required");
        }
        if (limit < 1 || window == null || window.compareTo(Duration.ofMillis(1)) < 0
                || window.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("Use a positive limit and a 1ms to 1 day window");
        }
        Long result = redis.execute(SCRIPT, List.of("ratelimit:v2:" + scopedBucket),
                Integer.toString(limit), Long.toString(window.toMillis()));
        if (result == null) throw new IllegalStateException("Missing rate-limit result");
        return result == 1L;
    }
}
