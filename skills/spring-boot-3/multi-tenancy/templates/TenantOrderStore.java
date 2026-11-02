package com.example.tenancy;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Shared-schema repository; no public unscoped read/write operation. */
public final class TenantOrderStore {
    private final JdbcTemplate jdbc;
    public TenantOrderStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void create(UUID id, String description) {
        jdbc.update("INSERT INTO tenant_orders (tenant_id, id, description) VALUES (?, ?, ?)",
            TenantContext.required().value(), id, description);
    }

    public Optional<Order> find(UUID id) {
        return jdbc.query("SELECT id, description FROM tenant_orders WHERE tenant_id = ? AND id = ?",
            (rs, row) -> new Order(rs.getObject("id", UUID.class), rs.getString("description")),
            TenantContext.required().value(), id).stream().findFirst();
    }

    public boolean update(UUID id, String description) {
        return jdbc.update("UPDATE tenant_orders SET description = ? WHERE tenant_id = ? AND id = ?",
            description, TenantContext.required().value(), id) == 1;
    }

    public boolean delete(UUID id) {
        return jdbc.update("DELETE FROM tenant_orders WHERE tenant_id = ? AND id = ?",
            TenantContext.required().value(), id) == 1;
    }

    public record Order(UUID id, String description) {}
}
