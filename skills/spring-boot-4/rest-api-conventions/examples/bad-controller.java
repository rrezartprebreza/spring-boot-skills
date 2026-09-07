// ❌ BAD — all the common mistakes in one controller

@RestController
@RequestMapping("/orders")          // preserve the project route prefix
public class OrderController {

    @Autowired                       // field injection
    private OrderRepository orderRepository;  // direct repo, no service layer

    @PostMapping
    public Order createOrder(@RequestBody CreateOrderRequest request) { // returns entity directly
        Order order = new Order();   // no factory method
        order.setEmail(request.getEmail());
        return orderRepository.save(order); // entity leakage; missing creation status
    }

    @GetMapping("/{id}")
    public Map<String, Object> getOrder(@PathVariable Long id) { // ID type is not the problem; authorization still matters
        Optional<Order> order = orderRepository.findById(id);
        if (!order.isPresent()) {
            return Map.of("error", "not found"); // ad-hoc error, no status code
        }
        return Map.of("data", order.get()); // raw map, entity leakage
    }

    @GetMapping
    public List<Order> getAllOrders() { // no pagination, returns all rows
        return orderRepository.findAll();
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        orderRepository.deleteById(id);
        return "deleted"; // 200 with body instead of 204
    }
}
