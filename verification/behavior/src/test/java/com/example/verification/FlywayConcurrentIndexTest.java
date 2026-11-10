package com.example.verification;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.yaml.snakeyaml.Yaml;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the shipped flyway-migrations good examples against PostgreSQL in an isolated schema.
 * CREATE INDEX CONCURRENTLY needs its own migration file AND a session-level Flyway lock.
 */
@SpringBootTest(classes = FlywayConcurrentIndexTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FlywayConcurrentIndexTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class App {}

    private static final String SCHEMA = "flyway_examples_check";
    private static final String LEGACY_COLUMN = "ALTER TABLE orders ADD COLUMN full_name VARCHAR(255);\n";

    @Autowired DataSource dataSource;
    @TempDir Path migrations;

    @BeforeEach void resetSchema() {
        new JdbcTemplate(dataSource).execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test void shippedExamplesApplyAndBuildValidConcurrentIndex() throws IOException {
        write("V1__init.sql", example("templates/V1__create_table_template.sql"));
        write("V2__legacy_full_name.sql", LEGACY_COLUMN);
        write("V4__add_shipping_address_to_orders.sql", example("examples/good-migration.sql"));
        write("V5__index_orders_shipping_address.sql", example("examples/good-migration-concurrent-index.sql"));

        assertEquals(4, flyway().migrate().migrationsExecuted);

        Boolean valid = new JdbcTemplate(dataSource).queryForObject(
                "SELECT i.indisvalid FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = ? AND c.relname = 'idx_orders_shipping_address'",
                Boolean.class, SCHEMA);
        assertEquals(Boolean.TRUE, valid);
    }

    @Test void concurrentIndexMixedIntoOneMigrationIsRejected() throws IOException {
        write("V1__init.sql", example("templates/V1__create_table_template.sql"));
        write("V2__legacy_full_name.sql", LEGACY_COLUMN);
        write("V4__mixed.sql", example("examples/good-migration.sql")
                + "\n" + example("examples/good-migration-concurrent-index.sql"));

        FlywayException error = assertThrows(FlywayException.class, () -> flyway().migrate());
        assertTrue(error.getMessage().contains("non-transactional"), error.getMessage());
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("filesystem:" + migrations)
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .load();
    }

    @Test void documentedDefaultRejectsUntrackedNonEmptySchema() throws IOException {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA " + SCHEMA);
        jdbc.execute("CREATE TABLE " + SCHEMA + ".legacy_orders (id INTEGER PRIMARY KEY)");
        jdbc.update("INSERT INTO " + SCHEMA + ".legacy_orders VALUES (42)");
        write("V2__new_table.sql", "CREATE TABLE next_orders (id INTEGER PRIMARY KEY);");

        assertThrows(FlywayException.class, () -> baselineFlyway().migrate());
        assertEquals(42, jdbc.queryForObject("SELECT id FROM " + SCHEMA + ".legacy_orders", Integer.class));
        assertNull(jdbc.queryForObject("SELECT to_regclass(?)", String.class, SCHEMA + ".next_orders"));
        assertNull(jdbc.queryForObject("SELECT to_regclass(?)", String.class, SCHEMA + ".flyway_schema_history"));
    }

    @Test void documentedDefaultStillMigratesEmptySchema() throws IOException {
        write("V1__new_table.sql", "CREATE TABLE next_orders (id INTEGER PRIMARY KEY);");
        assertEquals(1, baselineFlyway().migrate().migrationsExecuted);
    }

    @Test void explicitVerifiedBaselineAllowsOnlyLaterMigrations() throws IOException {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA " + SCHEMA);
        jdbc.execute("CREATE TABLE " + SCHEMA + ".legacy_orders (id INTEGER PRIMARY KEY)");
        write("V1__existing.sql", "CREATE TABLE legacy_orders (id INTEGER PRIMARY KEY);");
        write("V2__new_table.sql", "CREATE TABLE next_orders (id INTEGER PRIMARY KEY);");
        var flyway = baselineFlyway();
        flyway.baseline(); // Version 1 was inspected and is already represented by legacy_orders.
        assertEquals(1, flyway.migrate().migrationsExecuted);
        assertNotNull(jdbc.queryForObject("SELECT to_regclass(?)", String.class, SCHEMA + ".next_orders"));
    }

    private Flyway baselineFlyway() throws IOException {
        String markdown = example("SKILL.md");
        int fence = markdown.indexOf("```yaml\n");
        assertTrue(fence >= 0, "missing Flyway YAML configuration");
        int start = fence + "```yaml\n".length();
        int end = markdown.indexOf("```", start);
        assertTrue(end >= start, "unterminated Flyway YAML configuration");
        Map<?, ?> configuration = new Yaml().load(markdown.substring(start, end));
        Map<?, ?> spring = (Map<?, ?>) configuration.get("spring");
        Map<?, ?> settings = (Map<?, ?>) spring.get("flyway");
        boolean automaticBaseline = (Boolean) settings.get("baseline-on-migrate");
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA)
                .locations("filesystem:" + migrations)
                .baselineOnMigrate(automaticBaseline).baselineVersion("1").load();
    }

    private void write(String name, String sql) throws IOException {
        Files.writeString(migrations.resolve(name), sql, StandardCharsets.UTF_8);
    }

    private static String example(String path) throws IOException {
        try (InputStream in = FlywayConcurrentIndexTest.class.getResourceAsStream("/flyway-examples/" + path)) {
            assertNotNull(in, "missing shipped example " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
