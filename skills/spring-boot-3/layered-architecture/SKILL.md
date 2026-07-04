---
name: layered-architecture
description: >
  Use when generating or modifying any Spring Boot class — controllers, services, repositories,
  DTOs, mappers, or configuration. Enforces strict layer separation and prevents business logic
  from leaking across boundaries.
---

# Layered Architecture

## Layer Rules

```
@RestController        ← HTTP only. No business logic. No JPA entities in responses.
      ↓ DTOs
@Service               ← All business logic lives here. Orchestrates repositories.
      ↓ Domain objects / Entities
@Repository            ← Data access only. No business logic. Returns entities or projections.
      ↓ JPA / JDBC
Database
```

## Controller Layer
- Handles HTTP: parsing requests, validating input (`@Valid`), returning responses
- Calls ONE service method per endpoint — no orchestration in controllers
- Never returns `@Entity` classes directly — always map to response DTOs
- Never injects `@Repository` — always goes through a `@Service`
- Exception handling via `@ControllerAdvice`, never try/catch in controllers

```java
// ✅ GOOD
@PostMapping("/orders")
public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
    Order order = orderService.createOrder(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
}

// ❌ BAD — business logic in controller
@PostMapping("/orders")
public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
    if (request.getItems().isEmpty()) throw new RuntimeException("No items");
    Order order = orderRepository.save(new Order(request)); // direct repo access
    return ResponseEntity.ok(order); // returning entity
}
```

## Service Layer
- Contains all business logic, validation rules, and orchestration
- `@Transactional` lives here, not in controllers or repositories
- Constructor injection only — never `@Autowired` field injection
- One service per aggregate root (OrderService, not OrderAndPaymentService)
- Returns domain objects or DTOs — never `HttpServletRequest` / `HttpServletResponse`

```java
// ✅ GOOD
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        inventoryService.reserve(request.getItems());
        Order order = Order.from(request);
        return orderRepository.save(order);
    }
}

// ❌ BAD — field injection, HTTP concern in service
@Service
public class OrderService {
    @Autowired private OrderRepository orderRepository;

    public ResponseEntity<Order> createOrder(...) { ... } // HTTP type in service
}
```

## Repository Layer
- Extends `JpaRepository<Entity, ID>` or `CrudRepository`
- Custom queries via `@Query` or query derivation — no raw SQL unless unavoidable
- Returns entities or Spring Data Projections — never raw `Object[]`
- No business logic — pure data access

## DTOs
- Separate Request / Response DTOs — never use the same class for both
- Validation annotations (`@NotNull`, `@Size`, etc.) on Request DTOs only
- Static factory method `ResponseDto.from(Entity entity)` for mapping
- Use records for immutable DTOs (Java 16+)

```java
// ✅ GOOD
public record OrderResponse(UUID id, String status, List<LineItemResponse> items) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getStatus().name(),
            order.getItems().stream().map(LineItemResponse::from).toList());
    }
}
```

## Mapper Pattern
- Keep mapping logic out of controllers and services — use dedicated mapper classes or static factory methods
- Mapper is a plain class or utility — not a Spring bean unless it needs injected dependencies
- Entity → Response DTO: static method on the response DTO (`OrderResponse.from(order)`)
- Request DTO → Entity: static factory on the entity (`Order.from(request)`) or a mapper class
- Collection mapping: use `.stream().map(OrderResponse::from).toList()` — never manual loops

```java
// ✅ GOOD — dedicated mapper for complex mappings
public class OrderMapper {

    public static OrderResponse toResponse(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getStatus().name(),
            order.getItems().stream().map(OrderMapper::toLineItem).toList(),
            order.getCreatedAt()
        );
    }

    public static Order toEntity(CreateOrderRequest request, User user) {
        Order order = Order.create(request.customerEmail(), user);
        request.items().forEach(item ->
            order.addItem(item.productId(), item.quantity()));
        return order;
    }

    private static LineItemResponse toLineItem(OrderItem item) {
        return new LineItemResponse(item.getProductId(), item.getQuantity(), item.getPrice());
    }
}
```

## Configuration Layer
- `@Configuration` classes live in a `config/` package — never in `service/` or `controller/`
- Configuration never imports service or controller classes
- Use `@ConfigurationProperties` for type-safe config — never raw `@Value` for groups of related settings
- Bean definitions for infrastructure concerns only (RestTemplate, ObjectMapper, SecurityFilterChain)

## Cross-Cutting Concerns
- Logging: use `@Slf4j` — never `System.out.println`
- Validation: `@Valid` on controller parameters, custom validators as `@Component`
- Exception handling: single `@RestControllerAdvice` class, never try/catch in controllers
- Auditing: `@CreatedDate` / `@LastModifiedDate` with `@EnableJpaAuditing`

## Gotchas
- Agent tends to put `@Transactional` on controllers — move it to services
- Agent uses `@Autowired` field injection — always use constructor injection (`@RequiredArgsConstructor`)
- Agent returns `List<Entity>` from controllers — always map to `List<ResponseDto>`
- Agent creates `OrderAndInventoryService` god classes — split by aggregate
- Agent puts mapping logic inside controllers — extract to mapper class or DTO factory method
- Agent creates `@Configuration` classes that depend on `@Service` beans — configuration should only wire infrastructure
