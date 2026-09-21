// ❌ BAD — every common @Transactional mistake

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    public Order findById(UUID id) {                      // ambiguous not-found contract
        return orderRepository.findById(id).orElse(null);
    }

    @Transactional
    public void processOrders(List<UUID> orderIds) {
        for (UUID id : orderIds) {
            this.processSingle(id);                       // joins outer TX; no independent transaction
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)  // never reached via self-invocation
    private void processSingle(UUID id) {                   // private method — proxy can't intercept
        Order order = orderRepository.findById(id).orElseThrow();
        order.process();
    }

    @Transactional
    public void createOrder(CreateOrderRequest request) {
        try {
            Order order = Order.create(request.customerEmail());
            orderRepository.save(order);
            riskyExternalCall(order);
        } catch (Exception e) {
            log.error("Failed", e);                       // hides failure; may commit or remain rollback-only
        }
    }

    @Transactional                                        // missing rollbackFor = Exception.class
    public void updateOrder(UUID id, UpdateOrderRequest request) throws BusinessException {
        Order order = orderRepository.findById(id).orElseThrow();
        order.update(request);
        // checked exception won't trigger rollback without rollbackFor
        externalService.notify(order);
    }
}
