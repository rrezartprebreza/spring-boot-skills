-- GOOD — V4__add_shipping_address_to_orders.sql
-- Correct double-underscore naming, nullable column, multi-step rename.
-- The index for shipping_address lives in its own file:
-- good-migration-concurrent-index.sql (V5). CREATE INDEX CONCURRENTLY must not
-- share a migration with other statements — Flyway rejects mixed migrations.

-- Step 1: Add nullable column (safe for existing data — no lock, no rewrite)
ALTER TABLE orders ADD COLUMN shipping_address TEXT;

-- Step 2: Multi-step column rename (safe approach)
-- Never use ALTER TABLE RENAME COLUMN directly — it breaks code mid-deploy
-- Instead: add new → backfill → deploy code reading both → drop old
-- (assumes a legacy full_name column from an earlier migration)
ALTER TABLE orders ADD COLUMN customer_name VARCHAR(255);

-- Step 3: Backfill from old column
UPDATE orders SET customer_name = full_name WHERE customer_name IS NULL;

-- Note: The DROP COLUMN for 'full_name' goes in a LATER migration
-- after all application code reads from 'customer_name'
