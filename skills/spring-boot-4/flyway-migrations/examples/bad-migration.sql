-- ❌ BAD — V4_add_column.sql (single underscore — Flyway won't recognize this!)

-- Adding NOT NULL column without default — fails on tables with existing rows
ALTER TABLE orders ADD COLUMN shipping_address TEXT NOT NULL;

-- Direct column rename — breaks application code during rolling deploy
ALTER TABLE orders RENAME COLUMN full_name TO customer_name;

-- Creating index without CONCURRENTLY — locks entire table on PostgreSQL
CREATE INDEX idx_orders_status ON orders (status);

-- Seed data in migration — should be in @Profile("dev") seeder or R__ repeatable migration
INSERT INTO orders (id, customer_email, status)
VALUES ('550e8400-e29b-41d4-a716-446655440000', 'test@example.com', 'PENDING');

-- Modifying a previously-run migration (V2) to "fix" it
-- NEVER do this — Flyway checksums will fail in all deployed environments
