package com.example.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer());
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .disableCachingNullValues()
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(jsonSerializer()));

        // Per-cache TTL overrides
        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
            "orders", defaultConfig.entryTtl(Duration.ofMinutes(15)),
            "products", defaultConfig.entryTtl(Duration.ofHours(1)),
            "users", defaultConfig.entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .transactionAware()   // put/evict only after the surrounding transaction commits
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
    }

    // Records are final, so DefaultTyping.NON_FINAL writes no type id for them and every
    // cache read fails ("missing type id property '@class'"). The builder's defaultTyping(true)
    // types user classes too; the validator limits type ids to an allow-list instead of
    // LaissezFaire's "any class". BigDecimal is non-final, so java.math must be allowed.
    private GenericJackson2JsonRedisSerializer jsonSerializer() {
        ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .polymorphicTypeValidator(BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.example.")   // your DTO packages
                .allowIfSubType("java.util.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .build())
            .build();
        return GenericJackson2JsonRedisSerializer.builder()
            .objectMapper(mapper)
            .defaultTyping(true)
            .build();
    }
}
