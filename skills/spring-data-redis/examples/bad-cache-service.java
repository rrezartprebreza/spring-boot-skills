// ❌ BAD — caches entities, Java serialization, no TTL, missing @EnableCaching

// Missing @EnableCaching on configuration — annotations silently do nothing!

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Cacheable("orders")                              // no explicit key — relies on all params
    public Order findById(UUID id) {
        return orderRepository.findById(id)
            .orElseThrow();                           // caches JPA entity with lazy fields
                                                      // LazyInitializationException on cache hit!
    }

    @Cacheable("orders")
    public Order findByIdNullable(UUID id) {
        return orderRepository.findById(id)
            .orElse(null);                            // caches null — every miss stores null permanently
    }

    // No @CacheEvict on update — stale data served forever
    public Order update(UUID id, UpdateOrderRequest request) {
        Order order = orderRepository.findById(id).orElseThrow();
        order.update(request);
        return orderRepository.save(order);           // cache still has old version
    }

    // No TTL configured anywhere — cache grows unbounded until OOM
    // Using default Java serialization — slow, fragile, breaks on class changes
}
