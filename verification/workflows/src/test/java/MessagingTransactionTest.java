import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class MessagingTransactionTest {
    private final String url = System.getenv().getOrDefault(
        "TEST_DATABASE_URL", "jdbc:postgresql://localhost:55439/skills");
    private String schema;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;

    /** Allocate a per-test PostgreSQL namespace and use one datasource for claim and effect. */
    @BeforeEach void createIsolatedSchema() throws Exception {
        String name = "message_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, "skills", "skills");
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + name);
            schema = name;
        }
        var source = new DriverManagerDataSource(url, "skills", "skills");
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema);
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "10");
        properties.setProperty("options", "-c statement_timeout=5000 -c lock_timeout=4000");
        source.setConnectionProperties(properties);
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.execute("CREATE TABLE processed (event_id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE effects (order_id UUID PRIMARY KEY, applications INT NOT NULL)");
    }

    /** Delete only the namespace successfully created by this test. */
    @AfterEach void removeOwnedSchema() throws Exception {
        if (schema == null) return;
        try (var connection = DriverManager.getConnection(url, "skills", "skills");
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    /** Inject failure after the effect to prove the shipped consumer rolls back both writes. */
    private OrderCreatedConsumer consumer(boolean failAfterWrite) {
        return new OrderCreatedConsumer(transaction,
            id -> jdbc.update("INSERT INTO processed VALUES (?) ON CONFLICT DO NOTHING", id) == 1,
            event -> {
                jdbc.update("""
                    INSERT INTO effects VALUES (?, 1) ON CONFLICT (order_id)
                    DO UPDATE SET applications = effects.applications + 1
                    """, event.orderId());
                if (failAfterWrite) throw new IllegalStateException("injected failure");
            });
    }

    @Test void sequentialRedeliveryAppliesOnlyOnce() {
        var event = new OrderCreated(UUID.randomUUID(), UUID.randomUUID(), 1);
        var consumer = consumer(false);
        consumer.consume(event);
        consumer.consume(event);
        assertEquals(1, jdbc.queryForObject("SELECT applications FROM effects", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM processed", Integer.class));
    }

    @Test void failureRollsBackClaimAndEffectAndAllowsRedelivery() {
        var event = new OrderCreated(UUID.randomUUID(), UUID.randomUUID(), 1);
        assertThrows(IllegalStateException.class, () -> consumer(true).consume(event));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM processed", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM effects", Integer.class));
        consumer(false).consume(event);
        assertEquals(1, jdbc.queryForObject("SELECT applications FROM effects", Integer.class));
    }

    /** Release two callers together and observe the database effect, not just mock call counts. */
    @Test void concurrentRedeliveryCommitsOnlyOneEffect() throws Exception {
        var event = new OrderCreated(UUID.randomUUID(), UUID.randomUUID(), 1);
        var workers = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var consumer = consumer(false);
        try {
            java.util.concurrent.Callable<Void> task = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                consumer.consume(event);
                return null;
            };
            var first = workers.submit(task);
            var second = workers.submit(task);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertEquals(1, jdbc.queryForObject("SELECT applications FROM effects", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM processed", Integer.class));
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
