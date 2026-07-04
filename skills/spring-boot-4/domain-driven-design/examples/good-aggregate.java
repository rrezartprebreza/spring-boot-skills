// ✅ GOOD — rich domain model, typed IDs, behavior methods, domain events, no public setters

public class Order {

    private final OrderId id;
    private final CustomerId customerId;
    private final EmailAddress customerEmail;
    private OrderStatus status;
    private Money total;
    private final List<OrderItem> items = new ArrayList<>();
    @Transient
    private final List<Object> domainEvents = new ArrayList<>();

    // Static factory — enforces valid initial state
    public static Order create(CustomerId customerId, EmailAddress email) {
        Order order = new Order(OrderId.generate(), customerId, email, OrderStatus.DRAFT, Money.ZERO);
        return order;
    }

    // Behavior methods enforce invariants
    public void addItem(ProductId productId, String name, int quantity, Money unitPrice) {
        if (status != OrderStatus.DRAFT)
            throw new OrderNotModifiableException(id);
        if (quantity <= 0)
            throw new InvalidQuantityException(quantity);
        items.add(new OrderItem(productId, name, quantity, unitPrice));
        recalculateTotal();
    }

    public void place() {
        if (items.isEmpty())
            throw new EmptyOrderException(id);
        if (status != OrderStatus.DRAFT)
            throw new IllegalOrderStateTransitionException(status, OrderStatus.PLACED);
        this.status = OrderStatus.PLACED;
        domainEvents.add(OrderPlaced.of(this));
    }

    public void cancel(String reason) {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED)
            throw new IllegalOrderStateTransitionException(status, OrderStatus.CANCELLED);
        this.status = OrderStatus.CANCELLED;
        domainEvents.add(new OrderCancelled(id, customerId, reason, Instant.now()));
    }

    public List<Object> pullDomainEvents() {
        var events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void recalculateTotal() {
        this.total = items.stream()
            .map(OrderItem::lineTotal)
            .reduce(Money.ZERO, Money::add);
    }

    // No public setters — state changes only through behavior methods
    public OrderId getId() { return id; }
    public OrderStatus getStatus() { return status; }
    public Money getTotal() { return total; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
}

// Typed ID — value object
public record OrderId(UUID value) {
    public static OrderId generate() { return new OrderId(UUID.randomUUID()); }
    public static OrderId of(UUID value) { return new OrderId(Objects.requireNonNull(value)); }
}
