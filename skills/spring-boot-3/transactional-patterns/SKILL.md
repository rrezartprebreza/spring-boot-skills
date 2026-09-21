---
name: transactional-patterns
description: >
  Use when defining or debugging Spring Boot 3 database transaction boundaries, rollback,
  propagation, optimistic-lock retries, or commit-bound events. For duplicate HTTP
  commands use idempotency-patterns; for reactive transactions use webflux-reactive-patterns.
---

# Transactional Patterns

## Establish the boundary

- Inspect the project's Boot/Framework version, transaction manager, persistence technology,
  rollback configuration and caller before changing propagation.
- Place multi-step business transactions at the application/service boundary. Preserve
  Spring Data repository transactions; repository annotations are not inherently wrong.
- Default propagation is `REQUIRED` — joins existing transaction or creates one
- Use `readOnly = true` for appropriate read units of work. It is an optimization hint,
  not write prevention, authorization or automatic routing to a read replica.
- Keep existing architecture and transaction-manager choices. A local database transaction
  does not make writes to another database or an HTTP provider atomic.

The snippets below are illustrative; domain classes and imports come from the application.
Adapt the [service template](templates/TransactionalOrderService.java) and compare the
[good](examples/good-transactional-service.java) and [bad](examples/bad-transactional-service.java)
examples when editing a service.

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

Participating `REQUIRED` calls use the outer transaction's isolation, timeout and read-only
settings. Catching an inner failure does not clear its rollback-only marker; outer commit
may still throw `UnexpectedRollbackException`.

`REQUIRES_NEW` needs another connection while the outer transaction retains its resources.
Account for pool capacity and lock contention. Reserve independent commits for records
that must survive a failed operation (for example, an attempted action), not a success
audit row that claims a rolled-back order was created. Do not reference an uncommitted
parent row from an independent audit transaction.

```java
// REQUIRES_NEW — for audit logging that must survive rollback
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void logAuditEvent(AuditEvent event) {
    auditRepository.save(event); // commits independently of parent TX
}

// Illustrative: record an attempt for an existing order; failures are unchecked.
@Transactional
public void processOrder(Order order) {
    auditService.logAuditEvent(new AuditEvent("ORDER_START", order.getId()));
    try {
        // ... process, may throw
    } catch (RuntimeException e) {
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

// ✅ FIX — extract the independently transactional operation to another bean
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
// Default: RuntimeException and Error roll back; checked exceptions do not.
// Check project-wide rollback configuration before adding per-method rules.

@Transactional(rollbackFor = InsufficientInventoryException.class) // checked exception
public Order createOrder(CreateOrderRequest request) throws InsufficientInventoryException {
    ...
}

```

Never use `noRollbackFor` to recover from `OptimisticLockException`: the persistence
provider marks the transaction rollback-only. Roll it back and retry the whole unit of
work in a fresh transaction, with a bounded policy and a fresh entity read.

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
    order.updateStatus(newStatus); // conflict can surface at flush or commit
    return orderRepository.save(order);
}
```

Use the [version-specific retry guidance](../resilience-retry/SKILL.md) for dependencies
and annotation imports. A retrying facade must not already hold a transaction that each
attempt's `REQUIRED` method would join.

## Distributed Transactions (Saga Pattern)

A saga coordinates durable local steps; wrapping remote calls in `@Transactional`
does not implement one.

1. Commit the pending order, saga state and outgoing command in one local transaction.
2. Dispatch the command outside that transaction, using a stable provider idempotency key.
3. Persist each confirmed outcome and the next command atomically in another local transaction.
4. On rejection, persist failure state and a compensation command. Retry compensation
   independently, and reconcile timeouts where the remote outcome is unknown.

Do not save failure state and then throw a rollback-triggering exception from the same
transaction: that erases the state. Recovery must resume from durable state after a crash.
Use the project's existing workflow mechanism; see
[messaging/outbox](../event-driven-messaging/SKILL.md) and
[idempotency](../idempotency-patterns/SKILL.md) for delivery and duplicate effects.

## Side Effects After Commit

For best-effort local notifications, a commit-bound listener prevents delivery on rollback.
For required delivery, persist an outbox entry with the business change or use the project's
durable publication mechanism. `AFTER_COMMIT` alone cannot recover a process crash or retry
a failed delivery.

```java
// Publisher — inside the TX
@Transactional
public Order place(UUID id) {
    Order order = orderRepository.findById(id).orElseThrow();
    order.place();
    eventPublisher.publishEvent(new OrderPlaced(order.getId())); // published now; listener defers handling
    return orderRepository.save(order);
}

// Listener — runs ONLY if the TX commits successfully
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onOrderPlaced(OrderPlaced event) {
    emailService.sendConfirmation(event.orderId()); // best effort; no durable retry
}
```

`AFTER_COMMIT` runs after the DB commits, but resources may still be bound to the completed
transaction. Delegate database writes to a separate `REQUIRES_NEW` bean. Listener failure
cannot undo the committed business write; make delivery failures observable and recoverable.
Without an active transaction the listener is skipped by default. `@Async` does not add
durable delivery. See [domain events](../domain-driven-design/SKILL.md).

## Verification

Test through Spring proxies and verify committed data from a new transaction. Cover checked
versus unchecked failures, inner rollback-only propagation, optimistic conflicts at commit,
audit rollback, and listener behavior after commit and rollback. Use the production database
engine for isolation and contention tests. A test-wide rollback can hide after-commit behavior.

## Gotchas
- Agent removes repository transaction annotations - keep the unit of work and existing Spring Data behavior.
- Agent treats AFTER_COMMIT as durable messaging - use an outbox or persistent publication registry for required delivery.
- Agent uses readOnly for replica routing or write protection - neither follows from the flag alone.
- Agent commits success audit entries with REQUIRES_NEW - commit them with the business mutation.
- Agent suppresses optimistic-lock rollback - retry in a fresh transaction.
- Agent calls `@Transactional` methods on `this` — self-invocation bypasses proxy
- Agent expects checked exceptions to rollback — must add `rollbackFor`
- Agent uses `@Transactional` on `private` methods — Spring proxy can't intercept

## Official sources

- [Spring transaction propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)
- [TransactionalEventListener lifecycle](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html)
- [Jakarta Persistence optimistic-lock rollback semantics](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/optimisticlockexception)
