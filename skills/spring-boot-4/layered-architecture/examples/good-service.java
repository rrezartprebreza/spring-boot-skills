// ✅ GOOD — proper layer separation, constructor injection, @Transactional on service

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    public OrderResponse findById(UUID id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
        return OrderResponse.from(order);
    }

    public Page<OrderResponse> findAll(Pageable pageable) {
        return orderRepository.findAll(pageable).map(OrderResponse::from);
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        inventoryService.reserve(request.items());
        Order order = Order.create(request.customerEmail());
        request.items().forEach(item ->
            order.addItem(item.productId(), item.quantity()));
        return orderRepository.save(order);
    }

    @Transactional
    public void cancel(UUID id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
        order.cancel();
        inventoryService.release(order.getItems());
    }
}
