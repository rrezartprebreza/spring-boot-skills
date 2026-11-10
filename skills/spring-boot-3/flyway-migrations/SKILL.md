---
name: flyway-migrations
description: >
  Use when creating database migrations, schema changes, seed data, or any SQL that
  modifies database structure. Covers Flyway naming conventions, versioning, and safe
  migration patterns.
---

# Flyway Migrations

## File Naming Convention

```
src/main/resources/db/migration/

V{version}__{description}.sql       ← versioned (run once)
R__{description}.sql                ← repeatable (run when checksum changes)
U{version}__{description}.sql       ← undo (requires Flyway Teams)

Examples:
V1__create_users_table.sql
V2__create_orders_table.sql
V2.1__add_order_status_index.sql
V3__add_customer_email_to_orders.sql
R__create_reporting_views.sql
```

Rules:
- Double underscore `__` between version and description
- Underscore `_` for spaces in description
- Sequential versions — never go back and fill gaps
- Never modify a migration that has already run in any environment

## Example Migrations

```sql
-- V1__create_users_table.sql
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(50)  NOT NULL DEFAULT 'USER',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- V2__create_orders_table.sql
CREATE TABLE orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_amount    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user_id  ON orders(user_id);
CREATE INDEX idx_orders_status   ON orders(status);
CREATE INDEX idx_orders_created  ON orders(created_at DESC);

-- V3__add_shipping_address_to_orders.sql
-- Adding a column — always nullable or with default (safe for existing rows)
ALTER TABLE orders
    ADD COLUMN shipping_address TEXT,
    ADD COLUMN shipped_at TIMESTAMPTZ;
```

## Safe Migration Patterns

```sql
-- Safe: add nullable column
ALTER TABLE orders ADD COLUMN notes TEXT;

-- Safe: add column with default
ALTER TABLE orders ADD COLUMN priority INT NOT NULL DEFAULT 0;

-- Safe: rename via expand → migrate → contract, spread across deploys
-- Step 1 (V5): add new column (nullable — old instances keep inserting without it)
ALTER TABLE orders ADD COLUMN customer_email VARCHAR(255);
-- Step 2 (V5): backfill
UPDATE orders SET customer_email = (SELECT email FROM users WHERE users.id = orders.user_id);
-- Step 3 (V7, only after EVERY writer populates customer_email): add the check as NOT VALID —
-- instant, no table scan, but new rows are already enforced
ALTER TABLE orders ADD CONSTRAINT orders_customer_email_nn CHECK (customer_email IS NOT NULL) NOT VALID;
-- Step 4 (V8 — a SEPARATE migration, so the V7 lock is already released): validate without
-- blocking writes, then SET NOT NULL reuses the validated check and skips the scan (PG 12+)
ALTER TABLE orders VALIDATE CONSTRAINT orders_customer_email_nn;
ALTER TABLE orders ALTER COLUMN customer_email SET NOT NULL;
-- Step 5 (V9, after no deployed code reads user_id): drop old column
ALTER TABLE orders DROP COLUMN user_id;

-- Safe: add index CONCURRENTLY (no table lock in Postgres) — TWO rules, both required:
-- 1. Put it ALONE in its own migration file. Flyway detects CONCURRENTLY as
--    non-transactional; mixed with any other statement the migration is rejected:
--    "Detected both transactional and non-transactional statements".
-- 2. Set spring.flyway.postgresql.transactional-lock=false. Flyway's default
--    advisory lock holds an open transaction on a second connection, and
--    CONCURRENTLY waits for every open transaction — including Flyway's own.
--    The migration hangs forever. Session-level locks still block concurrent migrators.
-- V6__index_orders_email.sql (after V5 adds the column)
CREATE INDEX CONCURRENTLY idx_orders_email ON orders(customer_email);

-- Dangerous: rename column directly (breaks running app)
ALTER TABLE orders RENAME COLUMN user_id TO customer_id;

-- Dangerous: NOT NULL without default on large table (locks table)
ALTER TABLE orders ADD COLUMN priority INT NOT NULL; -- will fail on existing rows
```

## application.yml

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false  # reject non-empty schemas without migration history
    validate-on-migrate: true
    out-of-order: false         # enforce sequential execution
    postgresql:
      transactional-lock: false # required for CREATE INDEX CONCURRENTLY (see above)
```

## Onboarding an existing database

Keep automatic baselining disabled in normal application configuration. For a database
that already has tables but no Flyway history:

1. Back up the database and verify the target URL, database, schema and credentials.
2. Compare its schema with the migrations and explicitly select the version already present.
3. Run a one-time `flyway baseline -baselineVersion=<verified-version>` using that target.
4. Inspect `flyway info`, then migrate only the later versions. Never baseline an unknown
   or partially migrated schema just to silence a startup error.

This is different from a baseline migration script for new environments. Automatic
baselining removes Flyway's wrong-database safety check:
[official baseline-on-migrate documentation](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting).

## Seed Data (test/dev only)

```java
// Use Spring profiles, not Flyway, for seed data
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {
    private final UserRepository userRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() == 0) {
            userRepository.save(User.createAdmin("admin@dev.local", "password123"));
        }
    }
}
```

## Team Workflow: Concurrent Migrations
- Multiple developers creating migrations simultaneously will cause version conflicts
- Solution: use a shared tracker (Slack channel, wiki page) or timestamp-based versions (`V20260414_1__`)
- If two migrations target the same version, one developer must bump theirs
- Run `flyway info` before committing to check for version gaps or duplicates
- In CI/CD: run `flyway validate` as a pre-deploy step to catch conflicts early
- Never set `out-of-order: true` in production — it masks migration ordering bugs

## Gotchas
- Agent adds only `flyway-core` — since Flyway 10 (Boot 3.3+) database support lives in `flyway-database-<db>` modules (e.g. `flyway-database-postgresql`); without it startup fails with "Unsupported Database"
- Agent names files `V1_create_users.sql` (single underscore) — must be double `__`
- Agent modifies existing migration files — never edit a migration that has run
- Agent adds `NOT NULL` column without default — use nullable or provide default
- Agent renames columns directly — use multi-step add/backfill/drop across deploys
- Agent seeds data in Flyway migrations — use `@Profile("dev")` seeders instead
- Agent mixes `CREATE INDEX CONCURRENTLY` with other statements in one migration — Flyway rejects it ("both transactional and non-transactional statements"); give it its own file
- Agent ships a `CONCURRENTLY` migration without `spring.flyway.postgresql.transactional-lock=false` — the index build waits on Flyway's own advisory-lock transaction and deploys hang
- Agent skips indexes — always index foreign keys and columns used in WHERE/ORDER BY
- Agent creates migration with `DROP TABLE` or `DROP COLUMN` as first step — always add new column, deploy code, then drop old in a later migration
