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

## Executable shared-schema example

The [TenantOrderStore](templates/TenantOrderStore.java) and
[schema](examples/tenant-orders.sql) implement tenant-scoped reads, creates, updates and deletes.
This example uses blocking JDBC, a verified resource-server JWT and an active-tenant registry.
It is not a database/schema-routing or reactive implementation. Preserve the application's
existing ID types and isolation model rather than copying this UUID model indiscriminately.

Wire [TenantScopeFilter](templates/TenantScopeFilter.java) into the security chain before
`AuthorizationFilter` (after bearer authentication and exception translation). Supply a
`RequestMatcher` for only the tenant-protected routes and a server-side predicate checking the
tenant registry. Instantiate the filter inside the chain configuration, or disable its servlet
auto-registration if it is a bean, so it does not run twice. Missing authentication is 401;
missing, disabled or unknown tenant membership is 403. Do not read a public tenant header.

The verified `tenant_id` claim must be issued under a trusted tenant-membership policy; where
revocation must take effect before token expiry, consult current membership as well as the
active-tenant registry. Background jobs must similarly re-authorize tenant activity/membership
at execution time. A captured ID is context propagation, not perpetual authorization.

[TenantContext](templates/TenantContext.java) scopes work with try/finally cleanup, preserves
nested scopes and provides tenant-qualified cache keys:

```java
TenantId tenant = TenantAccess.resolve(authentication, tenantRegistry::isActive);
try (var scope = TenantContext.open(tenant)) {
    store.find(orderId);
    String key = TenantContext.orderCacheKey(orderId);
    executor.execute(TenantContext.capture(() -> store.find(orderId)));
}
```

Open the tenant scope before invoking a separate transactional service proxy. The example
does not propagate a transaction or SecurityContext to the worker; it starts its own unit of
work. Do not use `InheritableThreadLocal` with pooled workers. Servlet async dispatches need
explicit context propagation too. Scope every list, join and native query, and include
`tenant_id` in child foreign keys. No Java repository wrapper protects against arbitrary
unscoped SQL: restrict database credentials and consider PostgreSQL row-level security as
defense in depth.

Verify same-ID records in two tenants, forbidden cross-tenant reads/updates/deletes, distinct
cache keys, missing-context rejection, exception cleanup and sequential jobs on one worker.

## Official sources

- Hibernate multitenancy: https://docs.jboss.org/hibernate/orm/7.0/introduction/html_single/Hibernate_Introduction.html#multitenancy
- Reactor context: https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

## Verification scope

The repository fixture targets Boot 4.1.0 with Java 17/21 and checks
PostgreSQL 16 shared-schema access and blocking thread context; not Hibernate schema routing or Reactor. Check the
[coverage matrix](https://github.com/rrezartprebreza/spring-boot-skills/blob/main/verification/MATRIX.md)
before treating another minor version or an illustrative snippet as verified.

## Gotchas

- Agent trusts caller-supplied tenant headers - derive tenant from authentication.
- Agent resolves tenant after the transaction starts - routing may already be fixed.
- Agent scopes SQL but not caches or idempotency records - data can still cross tenants.
- Agent leaks servlet `ThreadLocal` state - always clear it in `finally`.
- Agent uses tenant IDs as unbounded metric tags - keep IDs in logs/traces or aggregate metrics.
