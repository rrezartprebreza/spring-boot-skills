---
name: spring-data-redis
description: >
  Use when implementing caching, session storage, rate limiting, or any Redis integration.
  Covers cache-aside pattern, key naming, TTL strategy, and serialization config.
---

# Spring Data Redis

## Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

## Configuration

```java
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer()); // JSON, not Java serialize
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer());
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer()))
            .disableCachingNullValues();

        var writer = RedisCacheWriter.create(factory,
            settings -> settings.immediateWrites().batchStrategy(BatchStrategies.scan(1000)));
        return RedisCacheManager.builder(writer)
            .cacheDefaults(config)
            .transactionAware()   // put/evict only after the surrounding transaction commits
            .withCacheConfiguration("orders", config.entryTtl(Duration.ofMinutes(5)))
            .withCacheConfiguration("products", config.entryTtl(Duration.ofHours(1)))
            .build();
    }

    // Jackson 3 (tools.jackson) serializer. Default typing is OFF by default —
    // enable it (scoped to trusted packages) so cached Objects round-trip to their
    // real type instead of coming back as LinkedHashMap.
    private GenericJacksonJsonRedisSerializer jsonSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
            .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.example.")   // your DTO packages
                .allowIfSubType("java.util.")     // collections
                .allowIfSubType("java.math.")     // BigDecimal is non-final, so it carries a type id
                .allowIfSubType("java.time.")
                .build())
            .build();
    }
}
```

## Key Naming Convention

This imperative example explicitly selects Redis 4 `immediateWrites()` so a completed put
has reached Redis before a following read, and write errors are not hidden in a background task.
That costs write latency; asynchronous cache writes are a separate, intentional consistency
choice. Transaction-aware callbacks still run after the database commit, not atomically with it.
`SCAN`-based clearing avoids a single `KEYS` traversal; this configuration assumes Lettuce.
See the [Redis 4 writer API](https://docs.spring.io/spring-data/redis/reference/api/java/org/springframework/data/redis/cache/RedisCacheWriter.RedisCacheWriterConfigurer.html).

```
{app}:{domain}:{id}          → orders:order:uuid-here
{app}:{domain}:list:{filter} → orders:order:list:status:PENDING
{app}:session:{userId}       → orders:session:uuid-here
{app}:ratelimit:{ip}         → orders:ratelimit:192.168.1.1
```

## @Cacheable — Declarative Caching

```java
@Service
@RequiredArgsConstructor
public class ProductService {

    @Cacheable(value = "products", key = "#id")
    public ProductResponse findById(UUID id) {
        return productRepository.findById(id)
            .map(ProductResponse::from)
            .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
    }

    @CachePut(value = "products", key = "#result.id")  // update cache after write
    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = productRepository.findById(id).orElseThrow();
        product.update(request);
        return ProductResponse.from(productRepository.save(product));
    }

    @CacheEvict(value = "products", key = "#id")  // invalidate on delete
    @Transactional
    public void delete(UUID id) {
        productRepository.deleteById(id);
    }

    @CacheEvict(value = "products", allEntries = true)  // clear all
    public void clearCache() {}
}
```

## Manual Cache-Aside Pattern

```java
@Service
@RequiredArgsConstructor
public class OrderCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JsonMapper jsonMapper; // Jackson 3 — Boot auto-configures a JsonMapper bean
    private static final Duration TTL = Duration.ofMinutes(5);

    public Optional<OrderResponse> get(UUID orderId) {
        String key = "orders:order:" + orderId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached == null) return Optional.empty();
        return Optional.of(jsonMapper.convertValue(cached, OrderResponse.class));
    }

    public void put(OrderResponse order) {
        String key = "orders:order:" + order.id();
        redisTemplate.opsForValue().set(key, order, TTL);
    }

    public void evict(UUID orderId) {
        redisTemplate.delete("orders:order:" + orderId);
    }
}
```

## Rate Limiting with Redis

```java
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final RedisTemplate<String, String> redisTemplate;

    // INCR and PEXPIRE in ONE atomic script: as two separate commands, a crash or
    // timeout between them leaves a key with no TTL and locks the caller out forever.
    private static final RedisScript<Long> INCR_WITH_TTL = RedisScript.of(
        "local c = redis.call('INCR', KEYS[1]) " +
        "if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end " +
        "return c", Long.class);

    public boolean isAllowed(String identifier, int maxRequests, Duration window) {
        String key = "ratelimit:" + identifier;
        Long count = redisTemplate.execute(INCR_WITH_TTL, List.of(key), String.valueOf(window.toMillis()));
        return count != null && count <= maxRequests;
    }
}
```

## application.yml

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 10
          max-idle: 5
          min-idle: 2
  cache:
    type: redis
```

## Cache Stampede

`sync = true` delegates loading coordination to the cache provider. The default Redis writer
in the configuration above is non-locking: do not claim that this annotation alone prevents
duplicate loads, even inside one application instance.

For expensive read-only loads, the optional
[CoalescingRedisCacheManager](templates/CoalescingRedisCacheManager.java) uses a locking
writer with a finite lock TTL. Register it under a distinct bean name, keeping your normal
cache manager primary, and pass a JSON-serialized `RedisCacheConfiguration`:

```java
@Cacheable(cacheManager = "coalescingCacheManager", value = "products", key = "#id", sync = true)
public ProductResponse findById(UUID id) { ... }
```

Add this bean method to the shown `RedisConfig`, reusing its `jsonSerializer()` helper,
and mark the normal `cacheManager` bean `@Primary` so existing annotations stay unchanged:

```java
@Bean("coalescingCacheManager")
RedisCacheManager coalescingCacheManager(RedisConnectionFactory factory) {
    var config = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofMinutes(5))
        .disableCachingNullValues()
        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer()));
    return CoalescingRedisCacheManager.create(factory, config, Duration.ofSeconds(10));
}
```

The writer lock is cache-wide, not per key: unrelated misses may block too. Configure finite
loader/transport timeouts and a lock TTL longer than the expected load, and test contention.
Lock expiry during a stalled load can still permit duplicates; this is load reduction, not
an exactly-once guarantee or protection for payment/order mutations. Redis command timeouts
do not bound the writer's entire lock-wait loop. Use a bounded caller deadline or a different
coalescing strategy when strict waiting bounds are required.

Use `sync` loading only for committed, read-only data; transaction-aware put/evict does not make
the loader-based cache get transactional. Prefer TTL jitter or local coalescing when the
cache-wide lock would reduce throughput excessively. Do not implement a bare `SETNX`/delete
lock without ownership tokens, atomic owner-checked release and a lease-expiry design.

See [Spring Redis cache behavior](https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html).

## Failure policy

| Usage | Redis unavailable | Recovery concern |
| --- | --- | --- |
| Disposable read cache | Optionally fall back to the source, with bounded concurrency | Avoid a database overload during an outage |
| Write-side eviction | Do not silently ignore failures | A committed DB update can leave stale cached values; use bounded TTL plus durable invalidation when needed |
| Authentication/session/authorization | Do not grant access because Redis failed | Return a controlled unavailable response or require reauthentication |
| Security rate limit | Fail closed or use an explicitly approved bounded fallback | Never silently allow unlimited traffic |

[OptionalCacheLookup](templates/OptionalCacheLookup.java) demonstrates narrow read-side
fallback for connection failures and timeouts, with an observability callback. It deliberately
does not suppress serialization errors or write/eviction failures. Recovery restores cache
reads; do not assume `transactionAware()` makes Redis and the database one atomic transaction.

Verification must include concurrent misses, TTL expiration, DTO round-trips (including
money/time fields), rollback, outage fallback and recovery. A short negative-cache TTL can be
useful for repeated misses; disabling null caching is an example choice, not a universal rule.

## Verification scope

The repository fixture targets Boot 4.1.0 with Java 17/21 and checks
Redis 7.4 serialization, TTL, normal concurrent loads and transaction callbacks; not failover or lease-expiry correctness. Check the
[coverage matrix](https://github.com/rrezartprebreza/spring-boot-skills/blob/main/verification/MATRIX.md)
before treating another minor version or an illustrative snippet as verified.

## Gotchas
- Agent assumes Redis 4 cache puts complete synchronously - select `immediateWrites()` when the contract requires read-after-write and caller-visible write failures.
- Agent combines `@Transactional` with `@CachePut`/`@CacheEvict` on a non-transaction-aware cache manager — the cache can be updated before commit (or evicted then re-filled with stale data); build it with `.transactionAware()`
- Agent implements a rate limiter as separate `INCR` then `EXPIRE` calls — a failure between them leaves a TTL-less key that blocks the caller forever; use one Lua script
- Agent allow-lists only `java.util.` for default typing — `BigDecimal` is non-final and carries a type id, so cached prices/totals fail to deserialize; allow `java.math.` (and `java.time.`) too
- Agent uses Java serialization for values — always use JSON (`GenericJacksonJsonRedisSerializer`)
- Agent caches entities with JPA lazy fields — cache DTOs/response objects, not entities
- Agent uses no TTL — always set expiry, memory is not infinite
- Agent forgets `@EnableCaching` — `@Cacheable` silently does nothing without it
- Agent caches misses without an expiry policy - either disable null caching or use an intentional short negative-cache TTL.
- Agent assumes `sync = true` guarantees a single load - verify the provider/writer and test concurrent misses.
- Agent gives every entry the same TTL — add jitter so keys don't expire in a synchronized wave
- Agent uses `GenericJackson2JsonRedisSerializer` — deprecated Jackson 2 API; use `GenericJacksonJsonRedisSerializer` (Jackson 3, `tools.jackson`)
- Agent expects default typing out of the box — the Jackson 3 serializer ships with it OFF; call `.enableDefaultTyping(validator)` or `@Cacheable` hits come back as `LinkedHashMap` and throw `ClassCastException`
- Agent registers `JavaTimeModule` on the mapper — Jackson 3 handles `java.time` natively; no module needed
- Agent declares a generic `ObjectMapper` bean to customize JSON — declare a `JsonMapper` bean or a `JsonMapperBuilderCustomizer` instead
- Migrating from Boot 3: Spring Session keys moved `spring.session.redis.*` → `spring.session.data.redis.*`
