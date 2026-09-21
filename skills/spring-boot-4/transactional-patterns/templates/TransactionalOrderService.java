package com.example.order.service;

import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.entity.Order;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // default: all public methods are read-only
public class TransactionalOrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    // Read-only hint; replica routing requires separate infrastructure.
    public Order findById(UUID id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
    }

    // Inherits class-level readOnly = true
    public Page<Order> findAll(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    // Override: write transaction with explicit rollback for checked exceptions
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(CreateOrderRequest request) {
        Order order = Order.create(request.customerEmail());
        order = orderRepository.save(order);
        eventPublisher.publishOrderCreated(order);
        return order;
    }

    // Override: write transaction
    @Transactional(rollbackFor = Exception.class)
    public void cancel(UUID id) {
        Order order = findById(id);
        order.cancel();
        eventPublisher.publishOrderCancelled(order);
    }
}

/**
 * Success audit entries must commit or roll back with the business operation.
 * MANDATORY prevents accidental use outside its transaction.
 * This template requires the application's domain, repository and audit types.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class OrderEventPublisher {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishOrderCreated(Order order) {
        auditLogRepository.save(AuditLog.orderCreated(order));
        log.debug("Creation audit staged for order {}", order.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishOrderCancelled(Order order) {
        auditLogRepository.save(AuditLog.orderCancelled(order));
        log.debug("Cancellation audit staged for order {}", order.getId());
    }
}
