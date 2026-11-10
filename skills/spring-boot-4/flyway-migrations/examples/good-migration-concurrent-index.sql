-- GOOD — V5__index_orders_shipping_address.sql
-- CREATE INDEX CONCURRENTLY builds the index without blocking writes on PostgreSQL.
-- Two requirements, both verified against Flyway 12 + PostgreSQL:
--   1. This statement is ALONE in the file. Flyway runs it outside a transaction;
--      adding any other statement makes Flyway reject the whole migration.
--   2. spring.flyway.postgresql.transactional-lock=false — otherwise the index
--      build waits forever on Flyway's own advisory-lock transaction.
-- If a CONCURRENTLY build fails midway it leaves an INVALID index behind:
-- DROP INDEX CONCURRENTLY it and re-run, rather than editing this file.

CREATE INDEX CONCURRENTLY idx_orders_shipping_address
    ON orders (shipping_address)
    WHERE shipping_address IS NOT NULL;
