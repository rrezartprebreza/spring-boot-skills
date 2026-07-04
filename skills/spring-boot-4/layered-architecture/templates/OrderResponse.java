package com.example.order.dto.response;

import com.example.order.entity.Order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    String status,
    List<LineItemResponse> items,
    Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getStatus().name(),
            order.getItems().stream()
                .map(item -> new LineItemResponse(
                    item.getProductId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getPrice()))
                .toList(),
            order.getCreatedAt()
        );
    }

    public record LineItemResponse(
        UUID productId,
        String productName,
        int quantity,
        java.math.BigDecimal price
    ) {}
}
