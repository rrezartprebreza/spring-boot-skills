-- ✅ GOOD — V4__add_shipping_address_to_orders.sql
-- Correct double-underscore naming, nullable column, safe index creation

-- Step 1: Add nullable column (safe for existing data — no lock, no rewrite)
ALTER TABLE orders ADD COLUMN shipping_address TEXT;

-- Step 2: Add index concurrently (non-blocking on PostgreSQL)
CREATE INDEX CONCURRENTLY idx_orders_shipping_address
    ON orders (shipping_address)
    WHERE shipping_address IS NOT NULL;

-- Step 3: Multi-step column rename (safe approach)
-- Never use ALTER TABLE RENAME COLUMN directly — it breaks code mid-deploy
-- Instead: add new → backfill → deploy code reading both → drop old
ALTER TABLE orders ADD COLUMN customer_name VARCHAR(255);

-- Step 4: Backfill from old column
UPDATE orders SET customer_name = full_name WHERE customer_name IS NULL;

-- Note: The DROP COLUMN for 'full_name' goes in a LATER migration
-- after all application code reads from 'customer_name'
