// ❌ BAD — hardcoded URLs, all links regardless of state, no assembler, missing self link

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable UUID id) {
        Order order = orderService.findById(id);
        OrderResponse response = OrderResponse.from(order);
        // Returns plain DTO — no HATEOAS links at all!
        return response;
    }

    @GetMapping("/{id}/with-links")
    public Map<String, Object> getWithLinks(@PathVariable UUID id) {
        Order order = orderService.findById(id);
        return Map.of(
            "data", OrderResponse.from(order),
            "_links", Map.of(
                // ❌ Hardcoded URLs instead of linkTo(methodOn(...))
                "self", "http://localhost:8080/api/v1/orders/" + id,
                "orders", "http://localhost:8080/api/v1/orders",
                // ❌ All action links regardless of order state
                "cancel", "http://localhost:8080/api/v1/orders/" + id + "/cancel",
                "ship", "http://localhost:8080/api/v1/orders/" + id + "/ship",
                "confirm", "http://localhost:8080/api/v1/orders/" + id + "/confirm"
            )
        );
    }

    @GetMapping
    public List<OrderResponse> list(Pageable pageable) {
        // ❌ Returns plain list — no CollectionModel, no pagination links
        return orderService.findAll(pageable).getContent().stream()
            .map(OrderResponse::from)
            .toList();
    }

    // ❌ Link-building logic scattered in controller instead of assembler
}
