package com.example.verification;

import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.request.CreateOrderRequest.OrderItemRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Validates the shipped layered-architecture request DTO with a real Bean Validation provider. */
class RequestValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll static void start() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll static void stop() { factory.close(); }

    @Test void validRequestHasNoViolations() {
        var request = new CreateOrderRequest("buyer@example.com",
                List.of(new OrderItemRequest(UUID.randomUUID(), 2)));
        assertEquals(Set.of(), paths(validator.validate(request)));
    }

    @Test void nestedItemConstraintsAreEnforced() {
        var request = new CreateOrderRequest("buyer@example.com",
                List.of(new OrderItemRequest(null, 0)));
        assertEquals(Set.of("items[0].productId", "items[0].quantity"), paths(validator.validate(request)));
    }

    @Test void emailAndItemsAreRequired() {
        var request = new CreateOrderRequest(" ", List.of());
        assertTrue(paths(validator.validate(request)).containsAll(Set.of("customerEmail", "items")));
    }

    private static Set<String> paths(Set<ConstraintViolation<CreateOrderRequest>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }
}
