// ❌ BAD — every common @Transactional mistake

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    public Order findById(UUID id) {                      // missing readOnly = true — DB can't optimize
        return orderRepository.findById(id).orElse(null);
    }

    @Transactional
    public void processOrders(List<UUID> orderIds) {
        for (UUID id : orderIds) {
            this.processSingle(id);                       // self-invocation — proxy is bypassed, no TX
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
            log.error("Failed", e);                       // swallows exception — rollback never triggers
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
