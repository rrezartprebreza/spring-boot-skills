# Project: [YOUR PROJECT NAME]

## Build & Run

```bash
./mvnw clean install        # build all modules
./mvnw spring-boot:run      # run locally
./mvnw test                 # run all tests
./mvnw verify               # build + integration tests
```

## Architecture

**Style:** [Layered / Hexagonal / DDD — pick one]  
**Modules:** [e.g. domain, application, infrastructure, web]  
**Java version:** 21  
**Spring Boot version:** 3.3.x

## Key Conventions

- Constructor injection always — never `@Autowired` on fields
- Response envelope: `ApiResponse<T>` wraps all REST responses
- IDs: UUID everywhere — never expose auto-increment Long in APIs
- Enums: `@Enumerated(EnumType.STRING)` always
- `@Transactional` on service layer only
- `@Transactional(readOnly = true)` on all read-only service methods

## Package Structure

```
com.example.{domain}/
├── controller/     ← @RestController, HTTP only
├── service/        ← @Service, business logic
├── repository/     ← @Repository, data access
├── dto/
│   ├── request/    ← inbound DTOs with @Valid annotations
│   └── response/   ← outbound DTOs, never expose entities
└── entity/         ← JPA @Entity classes
```

## Database

- **DB:** PostgreSQL 16
- **Migrations:** Flyway (`src/main/resources/db/migration/`)
- **Naming:** `V{n}__{description}.sql`
- Never modify a migration that has already run

## Testing

- Unit: `@ExtendWith(MockitoExtension.class)` — no Spring context
- Slice: `@WebMvcTest` for controllers, `@DataJpaTest` for repos
- Integration: `@SpringBootTest` + Testcontainers PostgreSQL
- Naming: `methodName_condition_expectedResult()`

## Skills Loaded

- `layered-architecture` — layer rules and patterns
- `rest-api-conventions` — response envelope, error handling
- `spring-data-jpa` — entity conventions, N+1 prevention
- `flyway-migrations` — migration naming and safe patterns
- `testing-pyramid` — test structure and naming
- [add more as needed]

## DO NOT

- Return `@Entity` classes from controllers — map to response DTOs
- Use `findAll()` without pagination on list endpoints
- Put `@Transactional` on controllers
- Use H2 for tests — use Testcontainers
- Store secrets in code — use environment variables
