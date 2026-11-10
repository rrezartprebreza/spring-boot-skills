package com.example.verification;

import com.example.tenancy.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import static org.junit.jupiter.api.Assertions.*;

class TenantIsolationTest {
    private static final TenantId A = new TenantId("tenant-a");
    private static final TenantId B = new TenantId("tenant-b");
    private static final String SCHEMA = "tenant_test_" + UUID.randomUUID().toString().replace("-", "");
    private static JdbcTemplate admin;
    private static TenantOrderStore store;

    @BeforeAll static void setup() throws Exception {
        String url = System.getenv().getOrDefault("TEST_DATABASE_URL", "jdbc:postgresql://localhost:55439/skills");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, "skills", "skills"));
        admin.execute("CREATE SCHEMA " + SCHEMA);
        var scoped = new JdbcTemplate(new DriverManagerDataSource(
            url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA, "skills", "skills"));
        try (var input = TenantIsolationTest.class.getResourceAsStream("/fixtures/tenant-orders.sql")) {
            assertNotNull(input);
            scoped.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        store = new TenantOrderStore(scoped);
    }
    @AfterAll static void dropOwnedSchema() { if (admin != null) admin.execute("DROP SCHEMA " + SCHEMA + " CASCADE"); }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        assertThrows(IllegalStateException.class, TenantContext::required);
    }

    @Test void sameIdIsIsolatedAcrossTenantsAndCacheKeys() {
        UUID id = UUID.randomUUID();
        var cache = new ConcurrentMapCache("orders");
        String keyA;
        try (var ignored = TenantContext.open(A)) {
            store.create(id, "a");
            keyA = TenantContext.orderCacheKey(id);
            cache.put(keyA, store.find(id).orElseThrow());
        }
        try (var ignored = TenantContext.open(B)) {
            assertTrue(store.find(id).isEmpty());
            assertFalse(store.update(id, "stolen"));
            assertFalse(store.delete(id));
            assertNotEquals(keyA, TenantContext.orderCacheKey(id));
            assertNull(cache.get(TenantContext.orderCacheKey(id)));
            store.create(id, "b");
            assertEquals("b", store.find(id).orElseThrow().description());
        }
        try (var ignored = TenantContext.open(A)) {
            assertEquals("a", store.find(id).orElseThrow().description());
            assertTrue(store.update(id, "a-updated"));
            assertTrue(store.delete(id));
        }
        try (var ignored = TenantContext.open(B)) {
            assertEquals("b", store.find(id).orElseThrow().description());
        }
    }
    @Test void unscopedRepositoryAccessFailsClosed() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> store.find(id));
        assertThrows(IllegalStateException.class, () -> store.create(id, "unscoped"));
        assertThrows(IllegalStateException.class, () -> store.update(id, "unscoped"));
        assertThrows(IllegalStateException.class, () -> store.delete(id));
    }
    @Test void tenantComesFromVerifiedAuthenticationNotHeaderAndCleansUpOnFailure() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authentication(A.value()));
        var request = new MockHttpServletRequest("GET", "/orders");
        request.addHeader("X-Tenant-Id", B.value());
        var filter = new TenantScopeFilter(req -> true, value -> value.equals(A.value()));
        assertThrows(IllegalArgumentException.class, () ->
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                assertEquals(A, TenantContext.required());
                throw new IllegalArgumentException("downstream failed");
            }));
        assertThrows(IllegalStateException.class, TenantContext::required);
    }
    @Test void missingUnknownOrDisabledTenantIsRejected() {
        assertThrows(InsufficientAuthenticationException.class, () -> TenantAccess.resolve(null, value -> true));
        assertThrows(AccessDeniedException.class, () -> TenantAccess.resolve(authentication(null), value -> true));
        assertThrows(AccessDeniedException.class, () -> TenantAccess.resolve(authentication(A.value()), value -> false));
    }
    @Test void pooledTasksPropagateOnlyCapturedTenantAndAlwaysCleanUp() throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        try {
            Runnable workA;
            try (var ignored = TenantContext.open(A)) {
                workA = TenantContext.capture(() -> {
                    assertEquals(A, TenantContext.required());
                    throw new IllegalArgumentException("job failed");
                });
            }
            var failed = worker.submit(workA);
            assertThrows(java.util.concurrent.ExecutionException.class, () -> failed.get(5, TimeUnit.SECONDS));
            worker.submit(() -> assertThrows(IllegalStateException.class, TenantContext::required)).get(5, TimeUnit.SECONDS);
            Runnable workB;
            try (var ignored = TenantContext.open(B)) {
                workB = TenantContext.capture(() -> assertEquals(B, TenantContext.required()));
            }
            worker.submit(workB).get(5, TimeUnit.SECONDS);
            worker.submit(() -> assertThrows(IllegalStateException.class, TenantContext::required)).get(5, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
    }
    @Test void nestedScopeRestoresOuterTenant() {
        try (var outer = TenantContext.open(A)) {
            try (var inner = TenantContext.open(B)) { assertEquals(B, TenantContext.required()); }
            assertEquals(A, TenantContext.required());
        }
    }

    private static JwtAuthenticationToken authentication(String tenant) {
        var jwt = Jwt.withTokenValue("test-only-verified-principal").header("alg", "RS256")
            .issuer("https://issuer.example").subject("user");
        if (tenant != null) jwt.claim("tenant_id", tenant);
        return new JwtAuthenticationToken(jwt.build(), List.of());
    }
}
