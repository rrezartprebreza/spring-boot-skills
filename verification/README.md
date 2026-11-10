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

Prerequisites: Maven, Java 17 or 21, disposable PostgreSQL 16 and Redis 7.4 instances. These tests
insert data and run migrations; never point them at a shared or production database.

```sh
docker run --detach --rm --name spring-skills-qa-db \
  -e POSTGRES_USER=skills -e POSTGRES_PASSWORD=skills -e POSTGRES_DB=skills \
  -p 127.0.0.1:55439:5432 postgres:16-alpine
docker exec spring-skills-qa-db pg_isready -U skills
docker run --detach --rm --name spring-skills-qa-redis \
  -p 127.0.0.1:56379:6379 redis:7.4-alpine
docker exec spring-skills-qa-redis redis-cli ping
mvn -f verification/behavior/pom.xml -Pboot3 clean test
mvn -f verification/behavior/pom.xml -Pboot4 clean test
docker stop spring-skills-qa-db
docker stop spring-skills-qa-redis
```

Wait until PostgreSQL is ready and Redis returns PONG before running Maven.
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
