// ✅ GOOD — proper response envelope, correct status, no entity leakage

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(
        @Valid @RequestBody CreateOrderRequest request
    ) {
        Order order = orderService.createOrder(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.ok(OrderResponse.from(order)));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(OrderResponse.from(orderService.findById(id)));
    }

    @GetMapping
    public ApiResponse<Page<OrderResponse>> list(Pageable pageable) {
        return ApiResponse.ok(orderService.findAll(pageable).map(OrderResponse::from));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        orderService.delete(id);
    }
}
