// ✅ GOOD — caches DTOs, JSON serialization, TTL configured, proper eviction

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;

    @Cacheable(value = "orders", key = "#id")
    public OrderResponse findById(UUID id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
        return OrderResponse.from(order);  // cache DTO, not entity
    }

    @Transactional
    @CachePut(value = "orders", key = "#result.id()")
    public OrderResponse update(UUID id, UpdateOrderRequest request) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
        order.update(request);
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional
    @CacheEvict(value = "orders", key = "#id")
    public void delete(UUID id) {
        orderRepository.deleteById(id);
    }

    @CacheEvict(value = "orders", allEntries = true)
    public void evictAllOrders() {
        // manual cache invalidation for admin use
    }
}
