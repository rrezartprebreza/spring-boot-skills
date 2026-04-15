package com.example.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(

    @NotBlank(message = "Customer email is required")
    @Email(message = "Must be a valid email address")
    String customerEmail,

    @NotEmpty(message = "Order must have at least one item")
    @Valid
    List<OrderItemRequest> items

) {
    public record OrderItemRequest(

        @NotBlank(message = "Product ID is required")
        UUID productId,

        @Positive(message = "Quantity must be positive")
        int quantity
    ) {}
}
