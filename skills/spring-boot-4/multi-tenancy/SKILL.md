---
name: multi-tenancy
description: >
  Use when implementing tenant resolution, database or schema isolation, Hibernate 7 tenancy,
  reactive tenant context, migrations, caches, jobs, or authorization in Spring Boot 4.
---

# Multi-Tenancy

Treat tenant identity as an authorization boundary, not a query convenience.

## Choose an isolation model

- Database per tenant: strongest isolation and highest operational cost.
- Schema per tenant: strong logical isolation with shared infrastructure.
- Shared schema with `tenant_id`: simplest operations, but every data path must enforce scope.
- Document the model and prohibit unrestricted repository access outside audited administration.

## Resolve tenant identity

- Derive tenant from a verified token claim, trusted host mapping, or authenticated API key.
- Reject missing, unknown, disabled, or conflicting tenant identifiers.
- Never trust a public `X-Tenant-Id` header by itself.
- Clear servlet thread-local state in `finally`; use Reactor `Context` for reactive flows.

## Enforce isolation

- Resolve tenant before opening a Hibernate session, transaction, or R2DBC connection.
- Include tenant in unique constraints, cache keys, idempotency keys, and object paths.
- Prevent cross-tenant joins and unrestricted native queries.
- Authorize support impersonation explicitly and audit the actor, tenant, reason, and duration.

## Operations

- Run migrations per database/schema with resumable progress and version reporting.
- Propagate tenant explicitly into messages, scheduled jobs, batch parameters, and async tasks.
- Apply quotas and bounded tenant labels for noisy-neighbor visibility.
- Test attempted cross-tenant reads, writes, cache access, and job execution.

## Examples

- See `examples/good-tenant-filter.java` and `examples/bad-tenant-filter.java`.

## Official sources

- Hibernate multitenancy: https://docs.jboss.org/hibernate/orm/7.0/introduction/html_single/Hibernate_Introduction.html#multitenancy
- Reactor context: https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

## Gotchas

- Agent trusts caller-supplied tenant headers - derive tenant from authentication.
- Agent resolves tenant after the transaction starts - routing may already be fixed.
- Agent scopes SQL but not caches or idempotency records - data can still cross tenants.
- Agent leaks servlet `ThreadLocal` state - always clear it in `finally`.
- Agent uses tenant IDs as unbounded metric tags - keep IDs in logs/traces or aggregate metrics.
