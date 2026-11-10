package com.example.verification;

import com.example.auth.config.SecurityConfig;
import com.example.auth.filter.JwtAuthenticationFilter;
import com.example.auth.service.JwtService;
import com.example.common.api.ApiResponse;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.request.CreateOrderRequest.OrderItemRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP + PostgreSQL fixture using shipped JWT, validation and envelope templates. */
@SpringBootTest(classes = OrderHttpTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")
class OrderHttpTest {
    private static final String BUYER = "buyer@example.com";
    private static final ParameterizedTypeReference<ApiResponse<OrderView>> RESPONSE =
        new ParameterizedTypeReference<>() {};
    @Value("${local.server.port}") int port;
    @Autowired JwtService jwt;
    @Autowired UserDetailsService users;
    @Autowired JdbcTemplate jdbc;

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, JwtService.class, JwtAuthenticationFilter.class})
    static class App {
        @Bean UserDetailsService users() {
            return name -> {
                if (!BUYER.equals(name)) throw new UsernameNotFoundException(name);
                return User.withUsername(name).password("unused").roles("USER").build();
            };
        }
        @Bean Orders controller(JdbcTemplate jdbc) { return new Orders(jdbc); }
    }

    // Minimal application-owned controller, not a claim to verify inventory or order business logic.
    record OrderView(UUID id, String customerEmail) {}
    @RestController
    static class Orders {
        private final JdbcTemplate jdbc;
        Orders(JdbcTemplate jdbc) { this.jdbc = jdbc; }
        @PostMapping("/api/v1/orders")
        ResponseEntity<ApiResponse<OrderView>> create(@Valid @RequestBody CreateOrderRequest request) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO orders (id, customer_email, status, total_amount) VALUES (?, ?, ?, ?)",
                id, request.customerEmail(), "PENDING", BigDecimal.ONE);
            return ResponseEntity.created(URI.create("/api/v1/orders/" + id))
                .body(ApiResponse.ok(new OrderView(id, request.customerEmail())));
        }
        @GetMapping("/api/v1/orders/{id}")
        ApiResponse<OrderView> get(@PathVariable UUID id) {
            return ApiResponse.ok(jdbc.queryForObject("SELECT id, customer_email FROM orders WHERE id = ?",
                (row, index) -> new OrderView(row.getObject("id", UUID.class), row.getString("customer_email")), id));
        }
    }

    private RestClient client() { return RestClient.create("http://localhost:" + port); }
    private String token() { return jwt.generateAccessToken(users.loadUserByUsername(BUYER)); }
    private CreateOrderRequest validRequest() {
        return new CreateOrderRequest(BUYER, List.of(new OrderItemRequest(UUID.randomUUID(), 1)));
    }

    @Test void authenticatedCreatePersistsAndCanBeRetrieved() {
        String token = token();
        var created = client().post().uri("/api/v1/orders")
            .headers(headers -> headers.setBearerAuth(token)).body(validRequest())
            .retrieve().toEntity(RESPONSE);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        assertTrue(created.getBody().success());
        UUID id = created.getBody().data().id();
        try {
            assertEquals(BUYER, jdbc.queryForObject("SELECT customer_email FROM orders WHERE id = ?", String.class, id));
            var retrieved = client().get().uri("/api/v1/orders/{id}", id)
                .headers(headers -> headers.setBearerAuth(token)).retrieve().body(RESPONSE);
            assertNotNull(retrieved);
            assertTrue(retrieved.success());
            assertEquals(created.getBody().data(), retrieved.data());
        } finally {
            jdbc.update("DELETE FROM orders WHERE id = ?", id);
        }
    }

    @Test void anonymousCreateIsRejected() {
        var failure = assertThrows(HttpClientErrorException.class, () ->
            client().post().uri("/api/v1/orders").body(validRequest()).retrieve().toBodilessEntity());
        assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
    }

    @Test void authenticatedEmptyItemsAreRejected() {
        String email = "invalid-" + UUID.randomUUID() + "@example.com";
        var failure = assertThrows(HttpClientErrorException.class, () ->
            client().post().uri("/api/v1/orders").headers(headers -> headers.setBearerAuth(token()))
                .body(new CreateOrderRequest(email, List.of())).retrieve().toBodilessEntity());
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM orders WHERE customer_email = ?", Integer.class, email));
    }
}
