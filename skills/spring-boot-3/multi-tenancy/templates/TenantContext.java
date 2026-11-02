package com.example.tenancy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/** Servlet/blocking example only. Reactor applications must use Reactor Context instead. */
public final class TenantContext {
    private static final ThreadLocal<TenantId> CURRENT = new ThreadLocal<>();
    private TenantContext() {}

    public static TenantId required() {
        TenantId tenant = CURRENT.get();
        if (tenant == null) throw new IllegalStateException("Tenant context required");
        return tenant;
    }

    public static Scope open(TenantId tenant) {
        TenantId previous = CURRENT.get();
        CURRENT.set(Objects.requireNonNull(tenant));
        return () -> {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        };
    }

    public static Runnable capture(Runnable task) {
        TenantId tenant = required(); // Capture at submission, never resolve on a pooled worker.
        return () -> {
            try (var ignored = open(tenant)) {
                task.run();
            }
        };
    }

    public static String orderCacheKey(UUID id) {
        String tenant = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(required().value().getBytes(StandardCharsets.UTF_8));
        return "orders:v1:tenant:" + tenant + ":order:" + id;
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override void close();
    }
}
