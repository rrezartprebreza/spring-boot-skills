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
-- ✅ Safe: add nullable column
ALTER TABLE orders ADD COLUMN notes TEXT;

-- ✅ Safe: add column with default
ALTER TABLE orders ADD COLUMN priority INT NOT NULL DEFAULT 0;

-- ✅ Safe: add index CONCURRENTLY (no table lock in Postgres)
CREATE INDEX CONCURRENTLY idx_orders_email ON orders(customer_email);

-- ✅ Safe: rename via add + backfill + drop (multi-step)
-- Step 1 (V5): add new column
ALTER TABLE orders ADD COLUMN customer_email VARCHAR(255);
-- Step 2 (V5): backfill
UPDATE orders SET customer_email = (SELECT email FROM users WHERE users.id = orders.user_id);
-- Step 3 (V5): add constraint after data is there
ALTER TABLE orders ALTER COLUMN customer_email SET NOT NULL;
-- Step 4 (later V6, after code is deployed): drop old column
ALTER TABLE orders DROP COLUMN user_id;

-- ❌ Dangerous: rename column directly (breaks running app)
ALTER TABLE orders RENAME COLUMN user_id TO customer_id;

-- ❌ Dangerous: NOT NULL without default on large table (locks table)
ALTER TABLE orders ADD COLUMN priority INT NOT NULL; -- will fail on existing rows
```

## application.yml

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true   # for existing databases
    validate-on-migrate: true
    out-of-order: false         # enforce sequential execution
```

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
- Agent names files `V1_create_users.sql` (single underscore) — must be double `__`
- Agent modifies existing migration files — never edit a migration that has run
- Agent adds `NOT NULL` column without default — use nullable or provide default
- Agent renames columns directly — use multi-step add/backfill/drop across deploys
- Agent seeds data in Flyway migrations — use `@Profile("dev")` seeders instead
- Agent skips indexes — always index foreign keys and columns used in WHERE/ORDER BY
- Agent creates migration with `DROP TABLE` or `DROP COLUMN` as first step — always add new column, deploy code, then drop old in a later migration
