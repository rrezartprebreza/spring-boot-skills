package com.example.clients;

import java.util.UUID;
import java.util.concurrent.CancellationException;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;

/** Bounded retry of an idempotent read. Register as a bean and invoke through its proxy. */
public class CatalogRetryService {
    private final CatalogTransport transport;
    public CatalogRetryService(CatalogTransport transport) { this.transport = transport; }

    @Retryable(retryFor = TransientCatalogException.class, maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 2, maxDelay = 100))
    public String find(UUID id) {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Interrupted");
        return transport.fetch(id);
    }

    @FunctionalInterface
    public interface CatalogTransport { String fetch(UUID id); }

    public static class TransientCatalogException extends RuntimeException {
        public TransientCatalogException(Throwable cause) { super("Catalog temporarily unavailable", cause); }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableRetry
    public static class RetryConfiguration {}
}
