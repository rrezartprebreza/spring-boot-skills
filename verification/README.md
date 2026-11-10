# Executable verification

The behavior fixture compiles the selected version tree's shipped JWT, Problem Details,
page DTO and configuration templates. It uses the shipped initial orders migration with
PostgreSQL and validates JPA mappings, version updates and rollback. Idempotency tests run the
shipped schema and check actual lock contention, rollback, tenant isolation and retained hashes.
They verify the database claim protocol, not a complete payment workflow.

The async fixture invokes the shipped worker through a Spring proxy and checks named executor
selection, thread-bound transaction isolation, failure propagation and bounded rejection/recovery.
Scheduling tests cover disabled/configured cron registration and the bounded adapter, not a
distributed scheduler, lease implementation or durable retry system. Redis tests exercise the
shipped Lua limiter against real Redis for concurrency, expiry, window retention and key scope.

The fixture also runs the shipped Flyway examples in an isolated schema, proving that
`CREATE INDEX CONCURRENTLY` applies from its own migration with a session-level lock and that
mixing it into a transactional migration is rejected. It validates the layered request DTO with a
real Bean Validation provider, checks that the envelope exception handler keeps framework client
errors (malformed JSON, 405, bad path variables, unknown URLs, method-security denials) out of 500,
and checks that the JWT chain leaves `/error` reachable without a token.

The Flyway tests read the documented YAML setting and verify refusal to migrate a non-empty
schema without history, successful empty-schema migration, and explicit baseline onboarding.
`OrderHttpTest` sends real HTTP requests to a random-port server with the shipped JWT security,
request validation and response envelope. A minimal JDBC controller checks create/retrieve,
401 for anonymous requests, and 400 without persistence for empty items. It does not test
inventory, a production order controller, or the application-specific SQL fixtures in the skill.

Prerequisites: Maven, Java 17 or 21, disposable PostgreSQL 16 and Redis 7.4 instances. These tests
insert data and run migrations; never point them at a shared or production database.

```sh
docker run --detach --rm --name spring-skills-qa-db \
  -e POSTGRES_USER=skills -e POSTGRES_PASSWORD=skills -e POSTGRES_DB=skills \
  -p 127.0.0.1:55439:5432 postgres:16-alpine
docker exec spring-skills-qa-db pg_isready -U skills
docker run --detach --rm --name spring-skills-qa-redis \
  -p 127.0.0.1:56389:6379 redis:7.4-alpine
docker exec spring-skills-qa-redis redis-cli ping
mvn -f verification/behavior/pom.xml -Pboot3 clean test
mvn -f verification/behavior/pom.xml -Pboot4 clean test
docker stop spring-skills-qa-db
docker stop spring-skills-qa-redis
```

Wait until pg_isready reports that PostgreSQL is accepting connections before running Maven.
Wait for Redis PONG too. Use TEST_REDIS_PORT to override its localhost port (default 56389).
Use TEST_DATABASE_URL to select a different disposable database URL. The fixture username
and password are both skills. Clean between Boot profiles to avoid stale compiled classes.
Set TEST_REDIS_PORT when Redis is mapped to another local port. Tests delete only their own
randomly scoped keys; never point the fixture at production or shared services.
To verify Java 21 compilation, select JDK 21 and pass -Dmaven.compiler.release=21.

CI runs all four Boot/Java combinations with independent PostgreSQL and Redis service containers.
The fixture baselines are Boot 3.5.16 and Boot 4.1.0; they do not claim coverage of every minor
release. Dependency versions are aligned through the respective Boot BOM.

The separate MCP fixture remains available:

```sh
mvn -f verification/spring-boot-4-mcp/pom.xml package
```

Illustrative snippets outside the selected source includes are not compiled. Agent
[response evaluations](../evaluations/README.md) are separate from Java regression tests.

## AI audit verification

```sh
mvn -f verification/ai/pom.xml -Pboot3 clean test
mvn -f verification/ai/pom.xml -Pboot4 clean test
```

These tests compile the shipped `AiAuditAdvisor` and `AiMetrics` directly, using Spring AI
1.0.0 with Boot 3.5.16 and Spring AI 2.0.0 with Boot 4.1.0. A real Spring async proxy and
bounded executor exercise saturation and shutdown; tests also cover accepted audit writes,
sanitized error types and persistence failure containment. The application-owned repository
and audit record are test contracts, not verified JPA mappings. No provider credentials or
network calls to AI services are needed.

The [coverage matrix](MATRIX.md) separates compiled sources, behavioral checks and agent evidence.

## Identity, tenancy, caching and client contracts

The behavior fixture now compiles the resource-server templates, shared-schema tenant example,
Redis configuration/coalescing/fallback templates and catalog HTTP/retry adapters from each tree.

- Identity tests use signed test JWTs through a real resource-server filter chain. They cover
  email changes/reuse, issuer/subject identity, invalid issuer/audience/expiry/signature and scopes.
- Tenant tests execute the shipped schema and repository against PostgreSQL, try same-ID
  cross-tenant reads and writes, check cache keys, and reuse one worker after a failed job.
- Redis tests use a real server and two independent cache managers. Non-locking loads overlap;
  configured locking coalesces normal concurrent loads. TTL, DTO money/time serialization and
  database transaction commit/rollback are verified. Outage exception injection checks the
  read-fallback policy, not network failover or write reconciliation.
- Client tests use real Spring retry proxies and a local stub HTTP server. Three-attempt bounds,
  permanent error rejection, pre-call interruption and a delayed-response timeout are checked.
  They do not establish a total caller deadline or interruption of an already-running request.

Boot 3 uses the BOM-managed Spring Retry line; Boot 4 uses core Framework resilience.
The Boot 3 fixture verifies an existing dependency, not a recommendation to adopt the
archived Spring Retry project for new applications.
Both HTTP adapters use RestClient (Boot 3.2+), not Boot 4 group-registration verification.
