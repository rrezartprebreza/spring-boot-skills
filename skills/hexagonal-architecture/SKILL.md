---
name: hexagonal-architecture
description: >
  Use when the project follows hexagonal (ports & adapters) architecture. Prevents domain
  code from depending on Spring or JPA. Use when you see packages like domain/, application/,
  infrastructure/, or adapters/ in the project structure.
---

# Hexagonal Architecture

## Package Structure

```
src/main/java/com/example/
├── domain/                     ← Pure Java. Zero framework dependencies.
│   ├── model/                  ← Entities, value objects, aggregates
│   ├── port/
│   │   ├── in/                 ← Use case interfaces (driving ports)
│   │   └── out/                ← Repository/external interfaces (driven ports)
│   └── service/                ← Domain services (pure business logic)
├── application/                ← Orchestrates use cases. Spring allowed here.
│   └── usecase/                ← @Service implementations of domain ports
└── infrastructure/             ← All framework/DB/HTTP details
    ├── persistence/            ← JPA adapters implementing out ports
    ├── web/                    ← REST controllers (driving adapters)
    └── external/               ← HTTP clients, messaging adapters
```

## Domain Layer — Zero Spring

```java
// domain/model/Order.java — pure Java, no annotations
public class Order {
    private final OrderId id;
    private final CustomerId customerId;
    private OrderStatus status;
    private final List<OrderItem> items;

    private Order(OrderId id, CustomerId customerId) {
        this.id = id;
        this.customerId = customerId;
        this.status = OrderStatus.PENDING;
        this.items = new ArrayList<>();
    }

    public static Order create(CustomerId customerId) {
        return new Order(OrderId.generate(), customerId);
    }

    public void addItem(ProductId productId, int quantity, Money price) {
        if (status != OrderStatus.PENDING)
            throw new OrderNotModifiableException(id);
        items.add(new OrderItem(productId, quantity, price));
    }

    // Getters only — no setters
}

// domain/model/OrderId.java — value object
public record OrderId(UUID value) {
    public static OrderId generate() { return new OrderId(UUID.randomUUID()); }
    public static OrderId of(String value) { return new OrderId(UUID.fromString(value)); }
}
```

## Ports — Interfaces Only

```java
// domain/port/in/CreateOrderUseCase.java — driving port
public interface CreateOrderUseCase {
    Order createOrder(CreateOrderCommand command);
}

// domain/port/in/CreateOrderCommand.java
public record CreateOrderCommand(CustomerId customerId, List<OrderItemData> items) {}

// domain/port/out/OrderRepository.java — driven port
public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(OrderId id);
    List<Order> findByCustomer(CustomerId customerId);
}

// domain/port/out/InventoryPort.java — driven port
public interface InventoryPort {
    void reserve(List<OrderItem> items);
    void release(List<OrderItem> items);
}
```

## Application Layer — Use Case Implementation

```java
// application/usecase/CreateOrderService.java
@Service  // Spring allowed here
@RequiredArgsConstructor
@Transactional
public class CreateOrderService implements CreateOrderUseCase {

    private final OrderRepository orderRepository;   // domain port (not JPA repo)
    private final InventoryPort inventoryPort;        // domain port

    @Override
    public Order createOrder(CreateOrderCommand command) {
        Order order = Order.create(command.customerId());
        command.items().forEach(item ->
            order.addItem(item.productId(), item.quantity(), item.price()));
        inventoryPort.reserve(order.getItems());
        return orderRepository.save(order);
    }
}
```

## Infrastructure — Adapters

```java
// infrastructure/persistence/JpaOrderRepository.java — implements domain port
@Repository
@RequiredArgsConstructor
public class JpaOrderRepository implements OrderRepository {

    private final SpringDataOrderRepository springDataRepo;
    private final OrderMapper mapper;

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = mapper.toEntity(order);
        return mapper.toDomain(springDataRepo.save(entity));
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return springDataRepo.findById(id.value()).map(mapper::toDomain);
    }
}

// Separate Spring Data interface — infrastructure detail
interface SpringDataOrderRepository extends JpaRepository<OrderJpaEntity, UUID> {}

// infrastructure/web/OrderController.java — driving adapter
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase; // injects use case port

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody CreateOrderRequest request) {
        Order order = createOrderUseCase.createOrder(request.toCommand());
        return ResponseEntity.status(201).body(ApiResponse.ok(OrderResponse.from(order)));
    }
}
```

## Gotchas
- Agent imports `javax.persistence` in domain classes — domain must be framework-free
- Agent injects `JpaRepository` directly into use cases — use domain port interfaces
- Agent puts `@Transactional` on domain services — belongs in application layer
- Agent mixes driving and driven ports — `port/in` = what app offers, `port/out` = what app needs
- Agent creates anemic domain with only getters/setters — behavior belongs on domain objects
