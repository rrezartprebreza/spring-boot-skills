package com.example.order.infrastructure.persistence;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.port.out.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Persistence adapter — implements domain port using JPA.
 * Domain layer never sees JPA, only this adapter does.
 */
@Component
@RequiredArgsConstructor
public class OrderJpaAdapter implements OrderRepository {

    private final SpringDataOrderRepository jpaRepository;
    private final OrderPersistenceMapper mapper;

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = mapper.toJpaEntity(order);
        OrderJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomainModel(saved);
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return jpaRepository.findById(id.value())
            .map(mapper::toDomainModel);
    }

    @Override
    public void deleteById(OrderId id) {
        jpaRepository.deleteById(id.value());
    }
}

/**
 * Spring Data JPA repository — internal to infrastructure layer.
 * Never exposed outside this package.
 */
interface SpringDataOrderRepository extends org.springframework.data.jpa.repository.JpaRepository<OrderJpaEntity, java.util.UUID> {
}
