package com.example.verification;

import com.example.redis.AtomicWindowLimiter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.RedisConnectionFailureException;
import static org.assertj.core.api.Assertions.*;

class RedisRateLimitTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private AtomicWindowLimiter limiter;
    private final List<String> ownedKeys = new ArrayList<>();

    @BeforeEach void connectToDisposableRedis() {
        int port = Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_PORT", "56389"));
        factory = new LettuceConnectionFactory("127.0.0.1", port);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        limiter = new AtomicWindowLimiter(redis);
    }

    @AfterEach void removeOnlyOwnedKeys() {
        try {
            if (redis != null && !ownedKeys.isEmpty()) redis.delete(ownedKeys);
        } finally {
            if (factory != null) factory.destroy();
        }
    }

    private String bucket() {
        String bucket = "qa:" + UUID.randomUUID();
        ownedKeys.add("ratelimit:v2:" + bucket);
        return bucket;
    }

    @Test void concurrentRequestsCannotExceedLimitAndAlwaysHaveExpiry() throws Exception {
        String bucket = bucket();
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 80; i++) results.add(pool.submit(() -> limiter.isAllowed(bucket, 10, Duration.ofSeconds(30))));
            int accepted = 0;
            for (var result : results) if (result.get(10, TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(10);
            assertThat(redis.opsForValue().get("ratelimit:v2:" + bucket)).isEqualTo("10");
            assertThat(redis.getExpire("ratelimit:v2:" + bucket, TimeUnit.MILLISECONDS)).isBetween(1L, 30000L);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void scopesAreIndependentAndExpiryRestoresCapacity() {
        String first = bucket(), second = bucket();
        Duration window = Duration.ofMillis(500);
        assertThat(limiter.isAllowed(first, 1, window)).isTrue();
        assertThat(limiter.isAllowed(first, 1, window)).isFalse();
        assertThat(limiter.isAllowed(second, 1, window)).isTrue();
        org.awaitility.Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> Boolean.FALSE.equals(redis.hasKey("ratelimit:v2:" + first)));
        assertThat(limiter.isAllowed(first, 1, window)).isTrue();
    }

    @Test void laterRequestDoesNotResetTheWindow() {
        String bucket = bucket();
        assertThat(limiter.isAllowed(bucket, 3, Duration.ofSeconds(30))).isTrue();
        // Move expiry close to the present to distinguish retention from a 30-second reset.
        redis.expire("ratelimit:v2:" + bucket, Duration.ofSeconds(5));
        assertThat(limiter.isAllowed(bucket, 3, Duration.ofSeconds(30))).isTrue();
        assertThat(redis.getExpire("ratelimit:v2:" + bucket, TimeUnit.MILLISECONDS)).isBetween(1L, 5000L);
    }

    @Test void invalidLimitsNeverCreateAKey() {
        String bucket = bucket();
        for (Duration invalid : List.of(Duration.ZERO, Duration.ofNanos(1), Duration.ofDays(2))) {
            assertThatThrownBy(() -> limiter.isAllowed(bucket, 1, invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> limiter.isAllowed(bucket, 0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(redis.hasKey("ratelimit:v2:" + bucket)).isFalse();
    }

    @Test void transportFailureIsNotConvertedToAnAllowedRequest() {
        StringRedisTemplate unavailable = new StringRedisTemplate() {
            @Override public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
                throw new RedisConnectionFailureException("simulated connection failure");
            }
        };
        assertThatThrownBy(() -> new AtomicWindowLimiter(unavailable)
                .isAllowed("qa:unavailable", 1, Duration.ofSeconds(1)))
                .isInstanceOf(RedisConnectionFailureException.class);
    }
}
