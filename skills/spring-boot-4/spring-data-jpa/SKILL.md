---
name: spring-data-jpa
description: >
  Use when generating or refactoring Spring Boot 4 JPA entities, repositories, queries, projections,
  persistence tests, entity relationships, embeddables, IDs, or Hibernate mappings. Covers Jakarta
  Persistence 3.2 imports, Hibernate 7 entity modeling, new-state detection, N+1 prevention,
  projections, keyset pagination, batch writes, and common agent mistakes.
---

# Spring Data JPA (Boot 4 / Hibernate 7)

Spring Boot 4 manages Jakarta Persistence 3.2, Jakarta Validation 3.1, and Hibernate ORM 7.x. Use
Boot dependency management and import `jakarta.persistence.*` / `jakarta.validation.*`. Do not add
explicit Hibernate, JPA, or Validator versions unless the project has a deliberate override policy.

## Entity Model Rules

Use an `@Entity` only for persistent state with identity and lifecycle. Use records for DTOs,
commands, and read models. Use `@Embeddable` for values stored inside an entity table.

```java
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_customer_id", columnList = "customer_id"),
    @Index(name = "idx_orders_status_created", columnList = "status, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Version
    private Long version;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Embedded
    private Money total;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Order create(UUID customerId) {
        Order order = new Order();
        order.customerId = Objects.requireNonNull(customerId);
        order.status = OrderStatus.DRAFT;
        order.total = Money.zero("EUR");
        return order;
    }

    public void addItem(UUID productId, int quantity, Money unitPrice) {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("Cannot edit submitted order");
        }
        items.add(OrderItem.create(this, productId, quantity, unitPrice));
        recalculateTotal();
    }

    private void recalculateTotal() {
        total = items.stream()
            .map(OrderItem::subtotal)
            .reduce(Money.zero("EUR"), Money::add);
    }
}
```

Rules:

- Use `jakarta.persistence.*`, never `javax.persistence.*`.
- Keep entities non-final with a protected no-arg constructor so Hibernate can instantiate/proxy them.
- Do not use Java records for ordinary entities. Records are good DTOs and sometimes embeddables.
- Use targeted Lombok (`@Getter`, protected `@NoArgsConstructor`), not `@Data` or broad `@Setter`.
- Prefer behavior methods and static factories over public setters/constructors.
- Initialize collections inline. JPA collection fields should not be null.
- Use `@Enumerated(EnumType.STRING)` with explicit column length. Never use `ORDINAL`.
- Add `@Version Long version` for user-editable aggregates. Use wrapper `Long`, not primitive `long`.
- Prefer `UUID` or pooled sequence IDs. Avoid `GenerationType.IDENTITY` on high-write tables because
  it disables insert batching.
- Validate request DTOs at the boundary; enforce entity invariants inside behavior methods.

## Embeddables and DTOs

```java
@Embeddable
public record Money(
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    BigDecimal amount,

    @Column(name = "currency", nullable = false, length = 3)
    String currency
) {
    public Money {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        return new Money(amount.add(other.amount), currency);
    }

    public Money multiply(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }
}
```

Never expose entities from controllers. Map entities to response records:

```java
public record OrderResponse(UUID id, String status, BigDecimal total, Instant createdAt) {
    static OrderResponse from(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getStatus().name(),
            order.getTotal().amount(),
            order.getCreatedAt());
    }
}
```

## Relationships

Map the database shape first. Prefer normal foreign keys: `@ManyToOne` on the owning side and
`@OneToMany(mappedBy = ...)` only when parent-to-child navigation is actually needed.

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_order"))
    private Order order;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    private int quantity;
    private Money unitPrice;

    static OrderItem create(Order order, UUID productId, int quantity, Money unitPrice) {
        OrderItem item = new OrderItem();
        item.order = Objects.requireNonNull(order);
        item.productId = Objects.requireNonNull(productId);
        item.quantity = quantity;
        item.unitPrice = Objects.requireNonNull(unitPrice);
        return item;
    }

    Money subtotal() {
        return unitPrice.multiply(quantity);
    }
}
```

- Put `fetch = FetchType.LAZY` on `@ManyToOne` and `@OneToOne`; to-one mappings are eager by default.
- Avoid unbounded bidirectional graphs. Add back-references only when required.
- Use `orphanRemoval = true` only when the parent truly owns the child's lifecycle.
- Avoid `@ManyToMany` for business relationships with attributes; model the join row as an entity.
- Do not serialize lazy relationships to JSON. Map to DTOs inside a transaction.

## equals and hashCode

Do not generate entity equality with Lombok `@Data`. It includes mutable fields and associations,
which can trigger lazy loading, recursion, and hash changes.

Preferred options:

- If the entity has a stable natural key, base equality on that key and enforce a unique database
  constraint.
- If it only has a generated ID, keep default object identity unless the project already has a
  proxy-safe generated-ID pattern.
- Never include collections, mutable fields, or associations in `equals`, `hashCode`, or `toString`.
- Use `instanceof`, not `getClass()`, when equality must work with Hibernate proxies.

```java
@Override
public boolean equals(Object other) {
    return other instanceof Customer that
        && email != null
        && email.equals(that.getEmail());
}

@Override
public int hashCode() {
    return email == null ? 0 : email.hashCode();
}
```

## Repositories and Query Patterns

```java
public interface OrderRepository extends JpaRepository<Order, UUID> {

    boolean existsByCustomerIdAndStatus(UUID customerId, OrderStatus status);

    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findById(UUID id);

    @Query("""
        select o
        from Order o
        where o.status = :status
        order by o.createdAt desc, o.id desc
        """)
    List<Order> findRecentByStatus(OrderStatus status, Limit limit);
}
```

Use:

- Derived queries for simple filters.
- `@Query` for explicit joins, keyset pagination, and complex predicates.
- `@EntityGraph` for bounded graph loading.
- Projections for read-only API views.
- `exists...` queries instead of `find...().isPresent()` checks.

Avoid:

- `findAll()` in endpoints.
- Native SQL unless JPQL cannot express the query or the database-specific feature is intentional.
- Returning entities for read-only list views when a projection is enough.

## N+1 Prevention

Identify N+1 by looking for lazy association access inside loops or JSON serialization of entities.

```java
@EntityGraph(attributePaths = {"items", "items.product"})
Optional<Order> findWithItemsAndProductsById(UUID id);

public interface OrderSummary {
    UUID getId();
    UUID getCustomerId();
    OrderStatus getStatus();
    Instant getCreatedAt();
}

List<OrderSummary> findByStatus(OrderStatus status);
```

Use fetch joins and entity graphs only for bounded relationships. For list endpoints, prefer
projections to avoid loading entire aggregate graphs.

## Pagination

Use `Pageable` for normal list screens:

```java
Page<Order> findByStatus(OrderStatus status, Pageable pageable);
```

Use keyset pagination for deep or infinite-scroll lists. `OFFSET` pagination scans and discards
skipped rows.

```java
@Query("""
    select o
    from Order o
    where o.status = :status
      and (o.createdAt < :lastCreatedAt
           or (o.createdAt = :lastCreatedAt and o.id < :lastId))
    order by o.createdAt desc, o.id desc
    """)
List<Order> findNextPage(OrderStatus status, Instant lastCreatedAt, UUID lastId, Limit limit);
```

The `(createdAt, id)` tuple keeps the cursor stable when timestamps collide. Back it with an index
like `(status, created_at desc, id desc)`.

## Batch Writes

Enable JDBC batching for write-heavy workloads:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc.batch_size: 50
        order_inserts: true
        order_updates: true
```

`GenerationType.IDENTITY` disables insert batching because Hibernate needs the generated key after
each row. Use UUIDs or pooled sequences when batch insert throughput matters.

## New-State Detection

Spring Data JPA detects new entities by nullable wrapper `@Version` first, then nullable ID. A
primitive version cannot be used because JPA treats `0` as the first persisted version.

For manually assigned IDs, add `@Version Long version` or implement `Persistable` with an `isNew`
flag cleared by `@PostPersist` and `@PostLoad`. Use the template in
`templates/BaseAssignedIdEntity.java`.

## Gotchas

- Agent imports `javax.persistence.*` - Boot 4 uses `jakarta.persistence.*`.
- Agent creates entity records - use records for DTOs/embeddables, not ordinary entities.
- Agent puts `@Data` on entities - generates setters and unsafe equality; use targeted `@Getter`.
- Agent makes entities `final` or constructors private - breaks Hibernate proxy/instantiation.
- Agent uses `FetchType.EAGER` - use `LAZY` on to-one and many-to-many relationships.
- Agent uses `@Enumerated(EnumType.ORDINAL)` - use `STRING`.
- Agent uses primitive `long version` - use nullable wrapper `Long`.
- Agent omits `@Version` on editable aggregates - lost updates are not detected.
- Agent returns entities from controllers - map to DTO records.
- Agent calls `findAll()` for list endpoints - require `Pageable`, `Limit`, or a projection query.
- Agent uses `OFFSET` pagination on huge tables - switch to keyset for deep pages.
- Agent includes lazy associations in equality or `toString` - causes lazy loads and recursion.
- Agent maps every relationship bidirectionally - add back-references only when required.
- Agent uses `@ManyToMany` for business links with attributes - model the join row as an entity.
- Agent batches inserts with `GenerationType.IDENTITY` - batching is silently off; use UUID/sequence.
