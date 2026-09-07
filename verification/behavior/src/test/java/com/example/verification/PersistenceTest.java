package com.example.verification;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = PersistenceTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PersistenceTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class App {}
    @Autowired Flyway flyway;
    @Autowired TransactionTemplate tx;
    @PersistenceContext EntityManager em;

    @Entity @Table(name = "orders")
    static class Order {
        @Id UUID id;
        @Column(name = "customer_email", nullable = false) String email;
        @Column(nullable = false) String status;
        @Column(name = "total_amount", nullable = false, precision = 19, scale = 2) BigDecimal total;
        @Version Long version;
        protected Order() {}
        Order(UUID id) { this.id=id; email="test@example.com"; status="PENDING"; total=BigDecimal.ONE; }
    }

    @Test void migratesAndPersistsWithOptimisticVersioning() {
        assertEquals(1, flyway.info().applied().length);
        UUID id=UUID.randomUUID();
        tx.executeWithoutResult(s -> em.persist(new Order(id)));
        tx.executeWithoutResult(s -> {
            Order order=em.find(Order.class, id);
            assertEquals("PENDING", order.status);
            assertEquals(0L, order.version);
            order.status="PAID";
        });
        tx.executeWithoutResult(s -> assertEquals(1L, em.find(Order.class,id).version));
    }
    @Test void rollsBackFailedUnitOfWork() {
        UUID id=UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            em.persist(new Order(id));
            em.flush();
            throw new IllegalStateException("abort");
        }));
        tx.executeWithoutResult(s -> assertNull(em.find(Order.class,id)));
    }
}
