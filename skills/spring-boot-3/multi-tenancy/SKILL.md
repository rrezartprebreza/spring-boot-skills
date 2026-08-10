---
name: multi-tenancy
description: >
  Use when implementing tenant resolution, database or schema isolation, tenant-aware JPA,
  reactive tenant context, migrations, caching, jobs, or authorization in Spring Boot 3.
---

# Multi-Tenancy

Treat tenant identity as an authorization boundary, not a query convenience.

## Choose an isolation model

- Database per tenant: strongest isolation and highest operational cost.
- Schema per tenant: strong logical isolation with shared infrastructure.
- Shared schema with `tenant_id`: simplest operations, but every access path must enforce scope.
- Document the selected model and prohibit repositories from bypassing it.

## Resolve tenant identity

- Derive the tenant from a verified token claim, trusted host mapping, or authenticated API key.
- Reject missing, unknown, disabled, or conflicting tenant identifiers.
- Never trust a public `X-Tenant-Id` header by itself.
- Clear servlet thread-local context in `finally`; use Reactor `Context` for reactive flows.

## Enforce isolation

- Apply tenant selection before opening the persistence session or transaction.
- Include tenant identity in unique constraints, cache keys, idempotency keys, and object storage paths.
- Prevent cross-tenant joins and unrestricted administrative repositories.
- Authorize support impersonation explicitly and audit every use.

## Operations

- Run migrations per database/schema with resumable progress and version reporting.
- Propagate tenant identity into scheduled jobs, messages, and async tasks explicitly.
- Limit noisy tenants with quotas and per-tenant observability using bounded identifiers.
- Test negative cross-tenant access, not only successful tenant queries.

## Examples

- See `examples/good-tenant-filter.java` and `examples/bad-tenant-filter.java`.

## Gotchas

- Agent trusts a tenant header supplied by the caller - derive tenant from authenticated context.
- Agent forgets to clear a servlet `ThreadLocal` - pooled threads can leak one tenant into another request.
- Agent scopes database queries but not cache keys - cached data can cross tenants.
- Agent starts a transaction before selecting the tenant - routing may choose the wrong database.
- Agent runs background jobs without tenant context - make tenant an explicit job parameter.
