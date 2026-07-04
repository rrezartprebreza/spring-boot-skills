// ❌ BAD — infrastructure leaks into domain, anemic model, no ports

@Service
@RequiredArgsConstructor
public class CreateOrderService {

    private final OrderJpaRepository orderRepository;     // JPA repo directly — no domain port
    private final RestTemplate restTemplate;               // infrastructure in application layer

    @Transactional
    public OrderEntity createOrder(CreateOrderRequest request) {
        // Business logic in service, not domain model (anemic)
        if (request.getItems().isEmpty()) {
            throw new RuntimeException("No items");       // generic exception, no domain meaning
        }

        OrderEntity order = new OrderEntity();            // JPA entity used as domain model
        order.setCustomerEmail(request.getEmail());       // setter-based, no invariant enforcement
        order.setStatus("PENDING");                       // string instead of enum/value object

        for (ItemRequest item : request.getItems()) {
            OrderItemEntity orderItem = new OrderItemEntity();  // direct child construction
            orderItem.setProductId(item.getProductId());
            orderItem.setQuantity(item.getQuantity());
            order.getItems().add(orderItem);              // bypasses aggregate root
        }

        // Infrastructure call mixed with business logic
        restTemplate.postForEntity(                       // HTTP call in service, not behind port
            "http://inventory-service/reserve",
            request.getItems(), Void.class);

        return orderRepository.save(order);               // returns JPA entity, not domain model
    }
}

// Domain model polluted with JPA
@Entity                                                   // JPA annotation in domain model
@Data                                                     // generates setters — no encapsulation
public class OrderEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                                      // Long ID, not typed value object
    private String customerEmail;
    private String status;                                // String instead of OrderStatus enum
    @OneToMany private List<OrderItemEntity> items;       // missing cascade, orphanRemoval
}
