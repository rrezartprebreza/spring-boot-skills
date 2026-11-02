package com.example.verification;

import com.example.config.CoalescingRedisCacheManager;
import com.example.config.OptionalCacheLookup;
import com.example.config.RedisConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class RedisContractTest {
    private static LettuceConnectionFactory factory;
    private static RedisCacheManager manager;
    private static RedisCacheConfiguration configuration;
    public record CachedProduct(UUID id, BigDecimal price, Instant updatedAt) {}

    @BeforeAll static void setup() {
        factory = new LettuceConnectionFactory(
            new RedisStandaloneConfiguration("127.0.0.1",
                Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_PORT", "56389"))),
            LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2)).build());
        factory.afterPropertiesSet();
        manager = new RedisConfig().cacheManager(factory);
        manager.afterPropertiesSet();
        var decorated = (TransactionAwareCacheDecorator) manager.getCache("products");
        configuration = ((RedisCache) decorated.getTargetCache()).getCacheConfiguration();
        assertNotNull(configuration);
    }
    @AfterAll static void cleanup() { if (factory != null) factory.destroy(); }

    @Test void recordMoneyAndTimestampRoundTripThroughShippedSerializer() {
        var value = new CachedProduct(UUID.randomUUID(), new BigDecimal("12.34"), Instant.parse("2026-01-01T00:00:00Z"));
        Cache cache = manager.getCache("products");
        cache.put(value.id().toString(), value);
        assertEquals(value, cache.get(value.id().toString(), CachedProduct.class));
        var template = new RedisConfig().redisTemplate(factory);
        template.afterPropertiesSet();
        String key = "test:product:" + UUID.randomUUID();
        template.opsForValue().set(key, value, Duration.ofSeconds(5));
        assertEquals(value, template.opsForValue().get(key));
    }

    @Test void entriesActuallyExpire() {
        var expiring = CoalescingRedisCacheManager.create(factory,
            configuration.entryTtl(Duration.ofMillis(200)), Duration.ofSeconds(10));
        expiring.afterPropertiesSet();
        Cache cache = expiring.getCache("expiry-" + UUID.randomUUID());
        cache.put("item", "value");
        assertEquals("value", cache.get("item", String.class));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertNull(cache.get("item")));
    }

    @Test void transactionAwareWritesCommitOrRollBackWithDatabaseTransaction() {
        var dataSource = new DriverManagerDataSource(
            System.getenv().getOrDefault("TEST_DATABASE_URL", "jdbc:postgresql://localhost:55439/skills"), "skills", "skills");
        var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Cache cache = manager.getCache("products");
        String rolledBack = UUID.randomUUID().toString();
        tx.executeWithoutResult(status -> {
            cache.put(rolledBack, "not committed");
            assertNull(cache.get(rolledBack));
            status.setRollbackOnly();
        });
        assertNull(cache.get(rolledBack));
        String committed = UUID.randomUUID().toString();
        tx.executeWithoutResult(status -> {
            cache.put(committed, "committed");
            assertNull(cache.get(committed));
        });
        assertEquals("committed", cache.get(committed, String.class));
    }

    @Test void nonLockingWriterAllowsTwoConcurrentLoads() throws Exception {
        Cache cache = manager.getCache("products");
        String key = UUID.randomUUID().toString();
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        Callable<String> loader = () -> { entered.countDown(); waitFor(release); return "value"; };
        try {
            Future<String> first = workers.submit(() -> cache.get(key, loader));
            Future<String> second = workers.submit(() -> cache.get(key, loader));
            assertTrue(entered.await(5, TimeUnit.SECONDS), "Both non-locking loaders should enter");
            release.countDown();
            assertEquals("value", first.get(5, TimeUnit.SECONDS));
            assertEquals("value", second.get(5, TimeUnit.SECONDS));
        } finally { release.countDown(); workers.shutdownNow(); }
    }

    @Test void rollbackPreservesCacheAndCommittedEvictionRemovesIt() {
        var tx = new TransactionTemplate(new DataSourceTransactionManager(new DriverManagerDataSource(
            System.getenv().getOrDefault("TEST_DATABASE_URL", "jdbc:postgresql://localhost:55439/skills"),
            "skills", "skills")));
        Cache cache = manager.getCache("products");
        String key = UUID.randomUUID().toString();
        cache.put(key, "original");
        tx.executeWithoutResult(status -> { cache.evict(key); status.setRollbackOnly(); });
        assertEquals("original", cache.get(key, String.class));
        tx.executeWithoutResult(status -> cache.evict(key));
        assertNull(cache.get(key));
    }

    @Test void failedLoaderReleasesTheCacheLock() {
        var locking = CoalescingRedisCacheManager.create(factory, configuration, Duration.ofSeconds(10));
        locking.afterPropertiesSet();
        Cache cache = locking.getCache("failed-load-" + UUID.randomUUID());
        assertThrows(Cache.ValueRetrievalException.class,
            () -> cache.get("item", () -> { throw new IllegalStateException("source unavailable"); }));
        assertTimeoutPreemptively(Duration.ofSeconds(2),
            () -> assertEquals("recovered", cache.get("item", () -> "recovered")));
    }

    @Test void lockingWriterCoalescesAcrossIndependentManagers() throws Exception {
        var firstManager = CoalescingRedisCacheManager.create(factory, configuration, Duration.ofSeconds(10));
        var secondManager = CoalescingRedisCacheManager.create(factory, configuration, Duration.ofSeconds(10));
        firstManager.afterPropertiesSet(); secondManager.afterPropertiesSet();
        String cacheName = "coalesced-" + UUID.randomUUID();
        Cache firstCache = firstManager.getCache(cacheName);
        Cache secondCache = secondManager.getCache(cacheName);
        var calls = new AtomicInteger();
        var entered = new CountDownLatch(1);
        var competing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = workers.submit(() -> firstCache.get("item", () -> {
                calls.incrementAndGet(); entered.countDown(); waitFor(release); return "value";
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<String> second = workers.submit(() -> {
                competing.countDown();
                return secondCache.get("item", () -> { calls.incrementAndGet(); return "duplicate"; });
            });
            assertTrue(competing.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(150, TimeUnit.MILLISECONDS));
            release.countDown();
            assertEquals("value", first.get(5, TimeUnit.SECONDS));
            assertEquals("value", second.get(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally { release.countDown(); workers.shutdownNow(); }
    }

    @Test void optionalReadFallbackIsNarrowAndRecoveryReturnsData() {
        var unavailable = new AtomicInteger();
        assertTrue(OptionalCacheLookup.read(() -> { throw new RedisConnectionFailureException("offline"); },
            unavailable::incrementAndGet).isEmpty());
        assertTrue(OptionalCacheLookup.read(() -> { throw new QueryTimeoutException("timeout"); },
            unavailable::incrementAndGet).isEmpty());
        assertEquals(2, unavailable.get());
        assertThrows(SerializationException.class, () -> OptionalCacheLookup.read(
            () -> { throw new SerializationException("invalid payload"); }, unavailable::incrementAndGet));
        Cache cache = manager.getCache("products");
        String key = UUID.randomUUID().toString();
        cache.put(key, "recovered");
        assertEquals("recovered", OptionalCacheLookup.read(
            () -> cache.get(key, String.class), unavailable::incrementAndGet).orElseThrow());
        assertEquals(2, unavailable.get());
    }

    @Test void observerFailureDoesNotBreakOptionalReadFallback() {
        assertTrue(OptionalCacheLookup.read(
            () -> { throw new RedisConnectionFailureException("offline"); },
            () -> { throw new IllegalStateException("observer unavailable"); }).isEmpty());
    }

    private static void waitFor(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Worker timed out");
    }
}
