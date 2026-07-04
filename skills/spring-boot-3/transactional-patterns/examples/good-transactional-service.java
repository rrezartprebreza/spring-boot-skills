// ✅ GOOD — class-level readOnly, method-level override, proper propagation, separate bean for REQUIRES_NEW

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderAuditService auditService;  // separate bean to avoid self-invocation

    public Order findById(UUID id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Page<Order> findAll(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(CreateOrderRequest request) {
        Order order = Order.create(request.customerEmail());
        order = orderRepository.save(order);
        auditService.logCreation(order);  // REQUIRES_NEW — audit persists even if outer rolls back
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(UUID id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
        order.cancel();
        auditService.logCancellation(order);
    }
}

@Service
@RequiredArgsConstructor
public class OrderAuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCreation(Order order) {
        auditLogRepository.save(AuditLog.orderCreated(order));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCancellation(Order order) {
        auditLogRepository.save(AuditLog.orderCancelled(order));
    }
}
