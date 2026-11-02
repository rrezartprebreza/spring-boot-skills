package com.example.verification;

import com.example.clients.CatalogHttpClient;
import com.example.clients.CatalogRetryService;
import com.example.clients.CatalogRetryService.CatalogTransport;
import com.example.clients.CatalogRetryService.TransientCatalogException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.HttpClientErrorException;
import static org.junit.jupiter.api.Assertions.*;

class CatalogRetryTest {
    private AnnotationConfigApplicationContext context(CatalogTransport transport) {
        var context = new AnnotationConfigApplicationContext();
        context.register(CatalogRetryService.RetryConfiguration.class);
        context.registerBean(CatalogTransport.class, () -> transport);
        context.registerBean(CatalogRetryService.class);
        context.refresh();
        return context;
    }

    @Test void retriesTransientReadAndStopsAfterSuccess() {
        var calls = new AtomicInteger();
        try (var context = context(id -> {
            if (calls.incrementAndGet() < 3) throw new TransientCatalogException(new java.net.ConnectException());
            return "item";
        })) {
            assertEquals("item", context.getBean(CatalogRetryService.class).find(UUID.randomUUID()));
            assertEquals(3, calls.get());
        }
    }
    @Test void exhaustionPreservesFinalFailure() {
        var calls = new AtomicInteger();
        var last = new TransientCatalogException(new java.net.ConnectException());
        try (var context = context(id -> { calls.incrementAndGet(); throw last; })) {
            assertSame(last, assertThrows(TransientCatalogException.class,
                () -> context.getBean(CatalogRetryService.class).find(UUID.randomUUID())));
            assertEquals(3, calls.get());
        }
    }
    @Test void permanentFailureIsNotRetried() {
        var calls = new AtomicInteger();
        try (var context = context(id -> { calls.incrementAndGet(); throw new IllegalArgumentException("invalid"); })) {
            assertThrows(IllegalArgumentException.class,
                () -> context.getBean(CatalogRetryService.class).find(UUID.randomUUID()));
            assertEquals(1, calls.get());
        }
    }
    @Test void interruptedCallerDoesNotStartRequest() {
        var calls = new AtomicInteger();
        try (var context = context(id -> { calls.incrementAndGet(); return "item"; })) {
            Thread.currentThread().interrupt();
            try {
                assertThrows(CancellationException.class,
                    () -> context.getBean(CatalogRetryService.class).find(UUID.randomUUID()));
                assertTrue(Thread.currentThread().isInterrupted());
                assertEquals(0, calls.get());
            } finally { Thread.interrupted(); }
        }
    }
    @Test void realHttpAdapterClassifiesTransientAndPermanentStatuses() throws Exception {
        var calls = new AtomicInteger();
        var status = new AtomicInteger(503);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/catalog", exchange -> {
            calls.incrementAndGet();
            byte[] body = "catalog".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            var client = CatalogHttpClient.create(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                Duration.ofSeconds(1), Duration.ofSeconds(1));
            try (var context = context(client)) {
                assertThrows(TransientCatalogException.class,
                    () -> context.getBean(CatalogRetryService.class).find(UUID.randomUUID()));
                assertEquals(3, calls.get());
                status.set(403); calls.set(0);
                assertThrows(HttpClientErrorException.Forbidden.class,
                    () -> context.getBean(CatalogRetryService.class).find(UUID.randomUUID()));
                assertEquals(1, calls.get());
            }
        } finally { server.stop(0); }
    }
    @Test void transportReadTimeoutIsBounded() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var workers = Executors.newSingleThreadExecutor();
        server.setExecutor(workers);
        server.createContext("/catalog", exchange -> {
            try { Thread.sleep(2000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            var client = CatalogHttpClient.create(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                Duration.ofSeconds(1), Duration.ofMillis(100));
            assertTimeoutPreemptively(Duration.ofSeconds(3),
                () -> assertThrows(TransientCatalogException.class, () -> client.fetch(UUID.randomUUID())));
        } finally { server.stop(0); workers.shutdownNow(); }
    }
}
