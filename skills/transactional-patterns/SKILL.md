---
name: transactional-patterns
description: >
  Use when working with @Transactional, multi-step database operations, distributed
  transactions, or any code that needs atomicity guarantees. Covers propagation rules,
  isolation levels, read-only optimization, and common pitfalls.
---

# Transactional Patterns

## Basic Rules

- `@Transactional` belongs on **service methods**, never controllers or repositories
- Default propagation is `REQUIRED` — joins existing transaction or creates one
- Always use on methods that write to the DB or coordinate multiple writes
- `@Transactional(readOnly = true)` on all read-only service methods — enables optimizations

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // default for all methods in this service
public class OrderService {

    @Transactional // overrides readOnly for writes
    public Order createOrder(CreateOrderRequest request) {
        inventoryService.reserve(request.items()); // participates in same TX
        return orderRepository.save(Order.from(request));
    }

    public Optional<Order> findById(UUID id) {
        return orderRepository.findById(id); // readOnly = true inherited
    }
}
```

## Propagation

| Propagation | Behavior |
|-------------|----------|
| `REQUIRED` (default) | Join existing TX or create new |
| `REQUIRES_NEW` | Always create new TX, suspend existing |
| `SUPPORTS` | Join if exists, proceed without TX if not |
| `NOT_SUPPORTED` | Always run without TX |
| `MANDATORY` | Must have existing TX, throw if not |
| `NEVER` | Must NOT have TX, throw if one exists |

```java
// REQUIRES_NEW — for audit logging that must survive rollback
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void logAuditEvent(AuditEvent event) {
    auditRepository.save(event); // commits independently of parent TX
}

// Order TX rolls back, audit log still saved
@Transactional
public void processOrder(Order order) {
    auditService.logAuditEvent(new AuditEvent("ORDER_START", order.getId()));
    try {
        // ... process, may throw
    } catch (Exception e) {
        auditService.logAuditEvent(new AuditEvent("ORDER_FAILED", order.getId()));
        throw e; // parent TX rolls back, audit TX already committed
    }
}
```

## Self-Invocation Pitfall

```java
// ❌ BROKEN — self-invocation bypasses Spring proxy, @Transactional ignored
@Service
public class OrderService {
    @Transactional
    public void processAll(List<UUID> ids) {
        ids.forEach(id -> this.processSingle(id)); // bypasses proxy!
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingle(UUID id) { ... } // never creates new TX
}

// ✅ FIX — inject self or extract to separate bean
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderProcessor orderProcessor; // separate bean

    @Transactional
    public void processAll(List<UUID> ids) {
        ids.forEach(id -> orderProcessor.processSingle(id)); // goes through proxy
    }
}
```

## Handling Exceptions

```java
// @Transactional rolls back on RuntimeException by default
// For checked exceptions, explicitly declare rollbackFor

@Transactional(rollbackFor = InsufficientInventoryException.class) // checked exception
public Order createOrder(CreateOrderRequest request) throws InsufficientInventoryException {
    ...
}

// noRollbackFor — for non-fatal exceptions you want to commit anyway
@Transactional(noRollbackFor = OptimisticLockException.class)
public void updateWithRetry(UUID id) { ... }
```

## Optimistic Locking

```java
@Entity
public class Order {
    @Version
    private Long version; // Hibernate handles conflicts automatically
}

// Handles concurrent updates
@Transactional
public Order updateStatus(UUID id, OrderStatus newStatus) {
    Order order = orderRepository.findById(id).orElseThrow();
    order.updateStatus(newStatus); // if another TX modified it, throws ObjectOptimisticLockingFailureException
    return orderRepository.save(order);
}
```

## Distributed Transactions (Saga Pattern)

For multi-service operations, use the Saga pattern instead of distributed TX:

```java
@Service
@RequiredArgsConstructor
public class OrderSaga {

    @Transactional
    public void execute(CreateOrderRequest request) {
        Order order = orderRepository.save(Order.create(request));
        try {
            inventoryClient.reserve(request.items());       // step 1
            paymentClient.charge(order.getId(), request.total()); // step 2
            order.confirm();
            orderRepository.save(order);
        } catch (PaymentException e) {
            inventoryClient.release(request.items()); // compensate step 1
            order.fail("Payment failed");
            orderRepository.save(order);
            throw e;
        }
    }
}
```

## Gotchas
- Agent puts `@Transactional` on controllers — only on service layer
- Agent forgets `readOnly = true` on read methods — missed DB optimization
- Agent calls `@Transactional` methods on `this` — self-invocation bypasses proxy
- Agent expects checked exceptions to rollback — must add `rollbackFor`
- Agent uses `@Transactional` on `private` methods — Spring proxy can't intercept
