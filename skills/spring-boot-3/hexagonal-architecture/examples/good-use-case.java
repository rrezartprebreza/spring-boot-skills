// ✅ GOOD — use case implements port, injects domain ports, no Spring annotations in domain

// --- Domain port (in domain layer, no Spring imports) ---
public interface CreateOrderUseCase {
    Order execute(CreateOrderCommand command);
}

public interface OrderRepository {  // domain port — not Spring's JpaRepository
    Order save(Order order);
    Optional<Order> findById(OrderId id);
}

// --- Application layer (implements use case, orchestrates domain) ---
@Service
@RequiredArgsConstructor
@Transactional
public class CreateOrderService implements CreateOrderUseCase {

    private final OrderRepository orderRepository;        // domain port, not JPA
    private final InventoryPort inventoryPort;             // domain port for external system

    @Override
    public Order execute(CreateOrderCommand command) {
        inventoryPort.reserve(command.items());
        Order order = Order.create(command.customerEmail(), command.items());
        return orderRepository.save(order);
    }
}

// --- Domain model (no JPA annotations, pure business logic) ---
public class Order {
    private final OrderId id;
    private final EmailAddress customerEmail;
    private OrderStatus status;
    private final List<OrderItem> items;

    public static Order create(EmailAddress email, List<OrderItemCommand> items) {
        Order order = new Order(OrderId.generate(), email, OrderStatus.PENDING, new ArrayList<>());
        items.forEach(i -> order.addItem(i.productId(), i.quantity()));
        return order;
    }

    public void addItem(ProductId productId, int quantity) {
        if (status != OrderStatus.PENDING)
            throw new OrderNotModifiableException(id);
        items.add(new OrderItem(productId, quantity));
    }
}
