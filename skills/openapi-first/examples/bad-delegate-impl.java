// ❌ BAD — modifies generated controller, mixes models, wrong config

// Editing the GENERATED controller directly — will be overwritten on next build!
@RestController
@RequestMapping("/api/v1/orders")
public class OrdersApiController {         // this file is generated — don't touch it

    @Autowired                             // field injection
    private OrderRepository orderRepository;

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id) {    // Long ID instead of UUID
        return orderRepository.findById(id)
            .orElseThrow();                           // returns domain entity, not generated DTO
    }

    @PostMapping
    public ResponseEntity<OrderDto> createOrder(@RequestBody OrderDto dto) {
        // Using generated DTO as both request AND response — spec has separate schemas
        Order order = new Order();
        order.setEmail(dto.getCustomerEmail());
        order.setStatus(dto.getStatus().name());      // mixes generated enum with domain
        orderRepository.save(order);
        return ResponseEntity.ok(dto);                // returns input DTO, not persisted state
    }
}

// Plugin config missing useSpringBoot3=true:
// <configOptions>
//     <useJakartaEe>false</useJakartaEe>              <!-- generates javax.* imports on Spring Boot 3 -->
//     <delegatePattern>false</delegatePattern>         <!-- no delegate — must edit generated code -->
// </configOptions>

// Generated sources committed to git — should be in .gitignore and target/
