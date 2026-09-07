---
name: idempotency-patterns
description: >
  Use when making retried HTTP commands or message processing safe against duplicate effects, including database-backed request keys, payload conflicts and concurrent retries. Do not apply caching as a substitute for business idempotency.
---

# Idempotency Patterns

## Spring Boot 4 baseline

The examples use Java 17 and Jakarta APIs supported by this Boot version. Keep dependencies managed by the project's Boot BOM.

## Define the contract

Identify the authenticated tenant/principal, operation, idempotency key, canonical request hash,
retention window and replayable result. Scope uniqueness by principal/tenant and operation;
a raw client key must never allow reading another user's result. Authenticate and authorize
before both initial execution and replay. Bound key length and stored response size.
Document what same-key/different-payload and still-in-progress requests return.

## Database-local effects

For PostgreSQL, [the schema and claim example](examples/good-idempotency.sql) uses a composite
primary key and INSERT ON CONFLICT DO NOTHING RETURNING. In a single database transaction:
claim the key, apply the business write, store the resulting status/body/selected headers,
then commit. Roll back the claim with the business mutation on failure.

If the insert returns no row, load the existing result in a subsequent statement under READ
COMMITTED, compare the canonical hash and replay only when it matches. A concurrent insert
waits on the uniqueness constraint; bound lock waits and return a documented retryable outcome
on timeout. With snapshot isolation, serialization failures require retrying the whole transaction.
Do not catch a unique-constraint violation and continue in an already-aborted transaction.

A result row and business write must use the same transaction manager and database. Return
the stored result rather than reconstructing it from mutable current entity state.
Keep replay headers allowlisted; do not persist cookies, bearer tokens or hop-by-hop headers.

## External effects and messages

A database transaction cannot atomically include an HTTP payment or email. Write an outbox record
with the business mutation and let a retryable worker perform the external effect using the
provider's idempotency support. Reconcile uncertain outcomes before retrying irreversible effects.
For messages, record a unique consumer/event pair and apply the projection in one transaction;
acknowledge only after commit. Retry failed transactions and route poison messages deliberately.

Define retention from the real client retry window. Deleting a key permits a later repeat to
execute again; do not present TTL as an exactly-once guarantee. Protect stored responses as
application data and delete them according to the project's retention policy.

## Verification

Test simultaneous identical requests, conflicting payloads, tenant separation, rollback after
claim, response replay after a lost connection and retries following a worker crash.
Use the production database engine for lock and isolation tests, not H2 compatibility mode.
[The bad example](examples/bad-idempotency.java) illustrates the check-then-act race.

## Gotchas

- Agent checks existence then inserts - enforce uniqueness in the database.
- Agent uses a JVM map or Redis TTL as the only business guard - coordinate with the actual write transaction.
- Agent replays another tenant's result - scope the key and re-authorize.
- Agent retries an external effect after a timeout blindly - reconcile or use provider idempotency.
- Agent deletes keys too early - document the retry window and expiry semantics.

## Official sources

- [PostgreSQL INSERT and ON CONFLICT](https://www.postgresql.org/docs/current/sql-insert.html)
- [PostgreSQL transaction isolation](https://www.postgresql.org/docs/current/transaction-iso.html)
- [Spring transaction management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
