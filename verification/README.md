# Executable verification

The behavior fixture compiles the selected version tree's shipped JWT, Problem Details,
page DTO and configuration templates. It uses the shipped initial orders migration with
PostgreSQL and validates JPA mappings, version updates and rollback. Idempotency tests run the
shipped schema and check actual lock contention, rollback, tenant isolation and retained hashes.
They verify the database claim protocol, not a complete payment workflow.

Prerequisites: Maven, Java 17 or 21, and a disposable PostgreSQL 16 database. These tests
insert data and run migrations; never point them at a shared or production database.

```sh
docker run --detach --rm --name spring-skills-qa-db \
  -e POSTGRES_USER=skills -e POSTGRES_PASSWORD=skills -e POSTGRES_DB=skills \
  -p 127.0.0.1:55439:5432 postgres:16-alpine
docker exec spring-skills-qa-db pg_isready -U skills
mvn -f verification/behavior/pom.xml -Pboot3 clean test
mvn -f verification/behavior/pom.xml -Pboot4 clean test
docker stop spring-skills-qa-db
```

Wait until pg_isready reports that PostgreSQL is accepting connections before running Maven.
Use TEST_DATABASE_URL to select a different disposable database URL. The fixture username
and password are both skills. Clean between Boot profiles to avoid stale compiled classes.
To verify Java 21 compilation, select JDK 21 and pass -Dmaven.compiler.release=21.

CI runs all four Boot/Java combinations with independent PostgreSQL service containers.
The fixture baselines are Boot 3.5.16 and Boot 4.1.0; they do not claim coverage of every minor
release. Dependency versions are aligned through the respective Boot BOM.

The separate MCP fixture remains available:

```sh
mvn -f verification/spring-boot-4-mcp/pom.xml package
```

Illustrative snippets outside the selected source includes are not compiled. Agent
[response evaluations](../evaluations/README.md) are separate from Java regression tests.
