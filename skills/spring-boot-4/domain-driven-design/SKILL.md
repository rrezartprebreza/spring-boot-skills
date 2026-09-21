---
name: domain-driven-design
description: >
  Use when evolving aggregate invariants, value objects or domain events in an existing
  DDD-style Spring Boot 4 application, or when DDD is explicitly requested. Preserve existing
  API identifiers and architecture when changing domain behavior.
---

# Domain-Driven Design

## Aggregate Rules
- One repository per aggregate root
- External code only accesses aggregate through root — never child entities directly
- Prefer references by ID across aggregate boundaries; preserve deliberate mappings.
- Choose boundaries from invariants that must commit atomically and expected contention,
  not a fixed number of child entities. Splitting requires an explicit consistency plan.

The snippets are illustrative and require the application's domain types and imports.
Keep existing public identifiers and persistence mappings unless the task includes a migration.

```java
// ✅ Aggregate root controls all access to children
order.addItem(productId, quantity); // through root
order.removeItem(itemId);           // through root

// ❌ Direct child access from outside
order.getItems().add(new OrderItem(...)); // bypasses invariants
```

## Value Objects

Immutable, no identity, equality by value:

```java
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Amount cannot be negative");
        Objects.requireNonNull(currency);
    }

    public Money add(Money other) {
        if (!currency.equals(other.currency))
            throw new CurrencyMismatchException(currency, other.currency);
        return new Money(amount.add(other.amount), currency);
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currency));
    }
}

public record EmailAddress(String value) {
    public EmailAddress {
        if (!value.matches("^[\\w.-]+@[\\w.-]+\\.[a-z]{2,}$"))
            throw new InvalidEmailException(value);
    }
}
```

## Domain Events

```java
// Event — immutable record
public record OrderPlaced(OrderId orderId, CustomerId customerId, Money total, Instant occurredAt) {
    public static OrderPlaced of(Order order) {
        return new OrderPlaced(order.getId(), order.getCustomerId(), order.getTotal(), Instant.now());
    }
}

// Collect events in aggregate, publish after save
@Entity
public class Order {
    @Transient
    private final List<Object> domainEvents = new ArrayList<>();

    public void place() {
        this.status = OrderStatus.PLACED;
        domainEvents.add(OrderPlaced.of(this));
    }

    public List<Object> pullDomainEvents() {
        var events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }
}

// Publish inside the transaction; save() is not commit.
@Service
@RequiredArgsConstructor
public class OrderApplicationService {
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Order placeOrder(PlaceOrderCommand command) {
        Order order = orderRepository.findById(command.orderId()).orElseThrow();
        order.place();
        Order saved = orderRepository.save(order);
        saved.pullDomainEvents().forEach(eventPublisher::publishEvent); // commit-bound listener below
        return saved;
    }
}

// Listen to events — bind to commit, not just publish.
// @EventListener fires synchronously inside the TX; if the TX later rolls back you've
// already sent the email. AFTER_COMMIT avoids rollback delivery but is best effort.
@Component
@RequiredArgsConstructor
public class OrderPlacedHandler {
    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlaced event) {
        emailService.sendOrderConfirmation(event.customerId(), event.orderId());
    }
}
```

> **Let Spring Data publish for you.** Instead of calling `pullDomainEvents()` by hand, expose a
> `@DomainEvents` method (returns the collected events) and an `@AfterDomainEventPublication` method
> (clears them) on the aggregate root. Spring Data's repository drains and publishes them automatically
> on every `save()` — no manual wiring in the service.

Neither publication mechanism guarantees durable delivery. For required external effects,
persist an outbox entry with the aggregate change or use a persistent publication registry,
with idempotent consumers and retry/recovery. `@Async` changes execution, not durability.
Post-commit database writes need a new transaction because the original resources may still
be bound after completion. See [transaction boundaries](../transactional-patterns/SKILL.md),
[messaging](../event-driven-messaging/SKILL.md) and [Modulith](../spring-modulith/SKILL.md).

## Specifications (complex queries)

```java
public class OrderSpecifications {
    public static Specification<Order> byStatus(OrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Order> byCustomer(UUID customerId) {
        return (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<Order> placedAfter(Instant date) {
        return (root, query, cb) -> cb.greaterThan(root.get("placedAt"), date);
    }
}

// Compose
Specification<Order> spec = OrderSpecifications.byStatus(PLACED)
    .and(OrderSpecifications.byCustomer(customerId))
    .and(OrderSpecifications.placedAfter(lastWeek));

orderRepository.findAll(spec, pageable);
```

## Anti-Corruption Layer (ACL)
- When integrating with external systems or legacy code, don't let their models leak into your domain
- Create an ACL — a translation layer that converts external data to your domain language
- ACL lives in infrastructure layer, not domain

```java
// ✅ GOOD — ACL translates external payment API to domain concepts
@Component
@RequiredArgsConstructor
public class PaymentGatewayAdapter implements PaymentPort {

    private final ExternalPaymentClient client;  // third-party SDK

    @Override
    public PaymentConfirmation charge(OrderId orderId, Money amount) {
        // Translate domain → external
        PaymentApiRequest apiRequest = new PaymentApiRequest(
            orderId.value().toString(),
            amount.amount(), // illustrative provider accepts BigDecimal
            amount.currency().getCurrencyCode());

        // Call external system
        PaymentApiResponse apiResponse = client.charge(apiRequest);

        // Translate external → domain
        return new PaymentConfirmation(
            PaymentId.of(apiResponse.getTransactionId()),
            apiResponse.isSuccessful() ? PaymentStatus.CONFIRMED : PaymentStatus.DECLINED);
    }
}
```

Use the provider's exact monetary representation. For integer minor units, convert with
the currency's fraction digits and exact arithmetic; do not silently round or convert
money to binary floating point.

## Verification

Test aggregate invariants, rollback preventing external notification, and recovery from
a crash after commit for durable publication. Verify that existing API IDs and database
relationships remain compatible with the requested change.

## Gotchas
- Agent creates anemic models with only getters/setters — put behavior on domain objects
- Agent replaces existing Long/UUID API IDs with value objects - preserve the public contract; map internal types explicitly.
- Agent puts domain logic in services — services should orchestrate, not decide
- Agent accesses child entities directly from outside — always go through aggregate root
- Agent confuses save with commit - publish in the transaction and select listener/durable delivery semantics deliberately.
- Agent splits an aggregate at an arbitrary child count - derive boundaries from business invariants.
- Agent lets external API models into domain — use an Anti-Corruption Layer to translate
- Agent adds nullable domain APIs without documenting them - follow the project's JSpecify conventions and mark nullable returns.
- Agent uses removed Boot 4 test annotations - use `@MockitoBean` in place of `@MockBean`.

## Official sources

- [Spring Data aggregate event publication](https://docs.spring.io/spring-data/jpa/reference/repositories/core-domain-events.html)
- [Spring transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
