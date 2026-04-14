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
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer()); // JSON, not Java serialize
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            .withCacheConfiguration("orders", config.entryTtl(Duration.ofMinutes(5)))
            .withCacheConfiguration("products", config.entryTtl(Duration.ofHours(1)))
            .build();
    }
}
```

## Key Naming Convention

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
    private final ObjectMapper objectMapper;
    private static final Duration TTL = Duration.ofMinutes(5);

    public Optional<OrderResponse> get(UUID orderId) {
        String key = "orders:order:" + orderId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached == null) return Optional.empty();
        return Optional.of(objectMapper.convertValue(cached, OrderResponse.class));
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

    public boolean isAllowed(String identifier, int maxRequests, Duration window) {
        String key = "ratelimit:" + identifier;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, window);
        }
        return count <= maxRequests;
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

## Gotchas
- Agent uses Java serialization for values — always use JSON (`GenericJackson2JsonRedisSerializer`)
- Agent caches entities with JPA lazy fields — cache DTOs/response objects, not entities
- Agent uses no TTL — always set expiry, memory is not infinite
- Agent forgets `@EnableCaching` — `@Cacheable` silently does nothing without it
- Agent caches `null` values — use `.disableCachingNullValues()` to avoid storing misses
