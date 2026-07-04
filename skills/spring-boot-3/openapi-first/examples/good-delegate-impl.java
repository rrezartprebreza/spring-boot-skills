// ✅ GOOD — implements generated delegate, never modifies generated code, maps between models

@Service
@RequiredArgsConstructor
public class OrderApiDelegateImpl implements OrdersApiDelegate {

    private final OrderService orderService;

    @Override
    public ResponseEntity<OrderDto> getOrderById(UUID id) {
        Order order = orderService.findById(id);
        return ResponseEntity.ok(toDto(order));
    }

    @Override
    public ResponseEntity<OrderDto> createOrder(CreateOrderRequestDto request) {
        Order order = orderService.createOrder(
            request.getCustomerEmail(),
            request.getItems().stream()
                .map(i -> new OrderItemCommand(i.getProductId(), i.getQuantity()))
                .toList());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(order));
    }

    @Override
    public ResponseEntity<PageOrderDto> listOrders(Integer page, Integer size) {
        Page<Order> orders = orderService.findAll(PageRequest.of(page, size));
        PageOrderDto dto = new PageOrderDto()
            .content(orders.getContent().stream().map(this::toDto).toList())
            .totalElements(orders.getTotalElements())
            .totalPages(orders.getTotalPages());
        return ResponseEntity.ok(dto);
    }

    // Map domain → generated DTO (never expose domain model via API)
    private OrderDto toDto(Order order) {
        return new OrderDto()
            .id(order.getId())
            .status(OrderDto.StatusEnum.fromValue(order.getStatus().name()))
            .customerEmail(order.getCustomerEmail())
            .createdAt(order.getCreatedAt());
    }
}
