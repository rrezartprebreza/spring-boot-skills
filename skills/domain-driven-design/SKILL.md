---
name: domain-driven-design
description: >
  Use when working with domain models, aggregates, value objects, domain events, or
  repositories in a DDD-style project. Ensures rich domain model over anemic CRUD.
---

# Domain-Driven Design

## Aggregate Rules
- One repository per aggregate root
- External code only accesses aggregate through root — never child entities directly
- Aggregates reference other aggregates by ID only, not direct object reference
- Keep aggregates small — if it has more than 3-4 child entities, split it

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

// Publish after successful save
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
        saved.pullDomainEvents().forEach(eventPublisher::publishEvent); // publish after commit
        return saved;
    }
}

// Listen to events
@Component
@RequiredArgsConstructor
public class OrderPlacedHandler {
    private final EmailService emailService;

    @EventListener
    @Async
    public void onOrderPlaced(OrderPlaced event) {
        emailService.sendOrderConfirmation(event.customerId(), event.orderId());
    }
}
```

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
            amount.amount().doubleValue(),
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

## Gotchas
- Agent creates anemic models with only getters/setters — put behavior on domain objects
- Agent uses `Long` for entity IDs — use typed value objects (`OrderId`, `CustomerId`)
- Agent puts domain logic in services — services should orchestrate, not decide
- Agent accesses child entities directly from outside — always go through aggregate root
- Agent publishes events before saving — publish after successful save/commit
- Agent lets external API models into domain — use an Anti-Corruption Layer to translate
