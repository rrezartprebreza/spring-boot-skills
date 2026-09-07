package com.example.verification;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

class IdempotencyTest {
    private String schema;
    private final UUID tenant = UUID.randomUUID();

    private Connection connect() throws SQLException {
        String url = System.getenv().getOrDefault("TEST_DATABASE_URL", "jdbc:postgresql://localhost:55439/skills");
        Connection connection = DriverManager.getConnection(url, "skills", "skills");
        if (schema != null) connection.setSchema(schema);
        return connection;
    }

    @BeforeEach void createIsolatedSchema() throws Exception {
        String name = "idempotency_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + name);
            schema = name;
            connection.setSchema(schema);
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("fixtures/good-idempotency.sql"));
        }
    }

    @AfterEach void removeOwnedSchema() throws Exception {
        if (schema != null) {
            try (Connection connection = connect(); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private boolean claim(Connection connection, UUID owner, String hash) throws SQLException {
        try (var statement = connection.prepareStatement("""
            INSERT INTO request_results (tenant_id, operation, idempotency_key, request_hash)
            VALUES (?, 'create-order', 'client-key', ?)
            ON CONFLICT (tenant_id, operation, idempotency_key) DO NOTHING
            RETURNING idempotency_key
            """)) {
            statement.setObject(1, owner);
            statement.setString(2, hash);
            try (var rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    @Test void concurrentDuplicateWaitsAndReplaysCommittedResult() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection first = connect()) {
            first.setAutoCommit(false);
            assertTrue(claim(first, tenant, "same-hash"));
            CountDownLatch started = new CountDownLatch(1);
            AtomicInteger backendPid = new AtomicInteger();
            Future<Boolean> duplicate = executor.submit(() -> {
                try (Connection second = connect()) {
                    second.setAutoCommit(false);
                    try (var limits = second.createStatement()) { limits.execute("SET LOCAL lock_timeout = '5s'"); }
                    try (var statement = second.createStatement(); var rows = statement.executeQuery("SELECT pg_backend_pid()")) {
                        rows.next();
                        backendPid.set(rows.getInt(1));
                    }
                    started.countDown();
                    boolean won = claim(second, tenant, "same-hash");
                    try (var statement = second.createStatement();
                            var rows = statement.executeQuery("SELECT response_status, response_body FROM request_results")) {
                        assertTrue(rows.next());
                        assertEquals(201, rows.getInt(1));
                        assertEquals("stored-order-result", rows.getString(2));
                    }
                    second.commit();
                    return won;
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
            boolean waiting = false;
            while (!waiting && System.nanoTime() < deadline) {
                try (var statement = first.prepareStatement("SELECT EXISTS (SELECT 1 FROM pg_locks WHERE pid=? AND NOT granted)")) {
                    statement.setInt(1, backendPid.get());
                    try (var rows = statement.executeQuery()) { rows.next(); waiting = rows.getBoolean(1); }
                }
                if (!waiting) Thread.sleep(10);
            }
            assertTrue(waiting, "duplicate must actually wait on the uncommitted unique key");
            try (var statement = first.createStatement()) {
                statement.executeUpdate("UPDATE request_results SET response_status=201, response_body='stored-order-result'");
            }
            first.commit();
            assertFalse(duplicate.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test void rollbackReleasesClaimForRetry() throws Exception {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            assertTrue(claim(connection, tenant, "hash"));
            connection.rollback();
            assertTrue(claim(connection, tenant, "hash"));
            connection.rollback();
        }
    }

    @Test void separatesTenantsAndRetainsOriginalHashOnConflict() throws Exception {
        try (Connection connection = connect()) {
            assertTrue(claim(connection, tenant, "original"));
            assertFalse(claim(connection, tenant, "different-payload"));
            assertTrue(claim(connection, UUID.randomUUID(), "another-tenant"));
            try (var statement = connection.prepareStatement("SELECT request_hash FROM request_results WHERE tenant_id=?")) {
                statement.setObject(1, tenant);
                try (var rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("original", rows.getString(1));
                }
            }
        }
    }
}
