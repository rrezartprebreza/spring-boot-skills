---
name: spring-data-jpa
description: >
  Use when generating JPA entities, repositories, queries, or anything touching the persistence
  layer. Covers entity conventions, N+1 prevention, projections, and query patterns.
---

# Spring Data JPA

## Entity Conventions

```java
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA requires no-arg, hide from callers
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String customerEmail;

    @Enumerated(EnumType.STRING) // always STRING, never ORDINAL
    @Column(nullable = false)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    // Static factory, not public constructor
    public static Order create(String customerEmail) {
        Order order = new Order();
        order.customerEmail = customerEmail;
        order.status = OrderStatus.PENDING;
        return order;
    }

    // Behavior on entity, not in service
    public void addItem(Product product, int quantity) {
        items.add(OrderItem.create(this, product, quantity));
    }
}
```

## Rules
- `@Enumerated(EnumType.STRING)` always — `ORDINAL` breaks on enum reordering
- `GenerationType.UUID` for IDs — never expose auto-increment integers
- `@NoArgsConstructor(access = PROTECTED)` — required by JPA, hidden from app code
- `@Getter` from Lombok — no `@Setter` on entities (use behavior methods)
- Collections initialized inline (`= new ArrayList<>()`) — never null

## N+1 Prevention

**Identify:** One query for orders + N queries for each order's items = N+1.

**Fix with JOIN FETCH:**
```java
@Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.id = :id")
Optional<Order> findByIdWithItems(@Param("id") UUID id);

// For lists — use @EntityGraph to avoid duplicates
@EntityGraph(attributePaths = {"items", "items.product"})
List<Order> findByStatus(OrderStatus status);
```

**Fix with Projections for read-only views:**
```java
// Interface projection — no entity loaded
public interface OrderSummary {
    UUID getId();
    String getCustomerEmail();
    OrderStatus getStatus();
    Instant getCreatedAt();
}

List<OrderSummary> findByStatus(OrderStatus status); // fast, no lazy loading issues
```

## Query Patterns

```java
public interface OrderRepository extends JpaRepository<Order, UUID> {

    // Derived query — simple conditions
    List<Order> findByStatusAndCustomerEmail(OrderStatus status, String email);

    // JPQL — for joins and complex conditions
    @Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.status = :status")
    List<Order> findActiveOrdersWithItems(@Param("status") OrderStatus status);

    // Native SQL — only when JPQL can't do it
    @Query(value = "SELECT * FROM orders WHERE created_at > NOW() - INTERVAL '7 days'",
           nativeQuery = true)
    List<Order> findRecentOrders();

    // Exists check — faster than findById + isPresent
    boolean existsByCustomerEmailAndStatus(String email, OrderStatus status);

    // Projection
    List<OrderSummary> findByCustomerEmail(String email);
}
```

## Pagination

```java
// Always use Pageable for list endpoints
Page<Order> findByStatus(OrderStatus status, Pageable pageable);

// In service
Page<Order> orders = orderRepository.findByStatus(status, PageRequest.of(page, size, Sort.by("createdAt").descending()));
```

## Bidirectional Relationships

```java
// Parent side (Order)
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
private List<OrderItem> items = new ArrayList<>();

// Child side (OrderItem) — owns the FK
@ManyToOne(fetch = FetchType.LAZY) // LAZY always on @ManyToOne
@JoinColumn(name = "order_id", nullable = false)
private Order order;

// Helper on parent to keep both sides in sync
public void addItem(OrderItem item) {
    items.add(item);
    item.setOrder(this);
}
```

## Gotchas
- Agent uses `FetchType.EAGER` — always use `LAZY` on `@ManyToOne` and `@ManyToMany`
- Agent uses `@Enumerated(EnumType.ORDINAL)` — always use `STRING`
- Agent uses `Long` IDs — use `UUID`
- Agent calls `findAll()` for list endpoints — always use `Pageable`
- Agent adds setters to entities — use behavior methods instead
- Agent forgets `orphanRemoval = true` on `@OneToMany` — child records become orphans
- Agent writes N+1 without realizing — check for `items` access in loops
