package com.example.order.config;

import com.example.order.entity.Order;
import com.example.order.entity.OrderItem;
import com.example.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
@Profile("dev")   // only runs in dev profile — never in production
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (orderRepository.count() > 0) {
            log.info("Dev data already seeded — skipping");
            return;
        }

        Order order1 = Order.create("alice@example.com");
        order1.addItem(UUID.randomUUID(), "Wireless Keyboard", 1, new BigDecimal("79.99"));
        order1.addItem(UUID.randomUUID(), "USB-C Hub", 2, new BigDecimal("34.99"));
        order1.confirm();

        Order order2 = Order.create("bob@example.com");
        order2.addItem(UUID.randomUUID(), "Monitor Stand", 1, new BigDecimal("129.99"));

        orderRepository.save(order1);
        orderRepository.save(order2);

        log.info("Seeded {} dev orders", 2);
    }
}
