// ❌ BAD — every common layering violation in one class

@Service
public class OrderService {

    @Autowired                                   // field injection instead of constructor
    private OrderRepository orderRepository;

    @Autowired
    private HttpServletRequest httpRequest;       // HTTP concern in service layer

    public ResponseEntity<Order> createOrder(CreateOrderRequest request) {  // returns HTTP type
        String token = httpRequest.getHeader("Authorization");              // reads HTTP headers in service
        if (request.getItems().isEmpty()) {
            return ResponseEntity.badRequest().build();                     // HTTP status logic in service
        }
        Order order = new Order();                                          // no factory method
        order.setEmail(request.getEmail());
        order.setStatus("PENDING");                                         // string instead of enum
        Order saved = orderRepository.save(order);                          // no @Transactional
        return ResponseEntity.ok(saved);                                    // returns entity directly
    }

    @Transactional
    public Order findById(Long id) {             // Long ID instead of UUID
        return orderRepository.findById(id)
            .orElse(null);                       // returns null instead of throwing
    }

    public List<Order> findAll() {               // no pagination, no readOnly
        return orderRepository.findAll();        // unbounded query
    }
}
