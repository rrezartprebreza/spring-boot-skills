package com.example.order.dto;

import com.example.order.entity.Order;
import com.example.order.entity.OrderItem;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import com.example.order.dto.response.LineItemResponse;

import java.util.List;

public class OrderMapper {

    private OrderMapper() {} // utility class

    public static OrderResponse toResponse(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getStatus().name(),
            toLineItems(order.getItems()),
            order.getCreatedAt()
        );
    }

    public static List<LineItemResponse> toLineItems(List<OrderItem> items) {
        return items.stream()
            .map(OrderMapper::toLineItem)
            .toList();
    }

    private static LineItemResponse toLineItem(OrderItem item) {
        return new LineItemResponse(
            item.getProductId(),
            item.getProductName(),
            item.getQuantity(),
            item.getPrice()
        );
    }
}
