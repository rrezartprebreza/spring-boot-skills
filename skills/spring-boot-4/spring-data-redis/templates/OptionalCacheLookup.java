package com.example.config;

import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

/** Read-only, disposable data caches. Never apply to authorization, sessions or rate limits. */
public final class OptionalCacheLookup {
    private OptionalCacheLookup() {}

    public static <T> Optional<T> read(Supplier<T> lookup, Runnable recordUnavailable) {
        try {
            return Optional.ofNullable(lookup.get());
        } catch (RedisConnectionFailureException | QueryTimeoutException unavailable) {
            try {
                recordUnavailable.run();
            } catch (RuntimeException observerFailure) {
                org.slf4j.LoggerFactory.getLogger(OptionalCacheLookup.class)
                    .warn("Optional cache observer failed: {}", observerFailure.getClass().getSimpleName());
            }
            return Optional.empty();
        }
    }
}
