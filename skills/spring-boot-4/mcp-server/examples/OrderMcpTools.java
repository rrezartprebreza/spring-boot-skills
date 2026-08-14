package com.example.mcp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public final class OrderMcpTools {

    private final OrderService orderService;

    public OrderMcpTools(OrderService orderService) {
        this.orderService = orderService;
    }

    @McpTool(
        name = "get_order",
        description = "Get an order by UUID with line items, status, and creation time",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true))
    public OrderResponse getOrder(
            @McpToolParam(description = "Order UUID", required = true) String orderId) {
        return OrderResponse.from(orderService.findById(UUID.fromString(orderId)));
    }
}

interface OrderService {
    Order findById(UUID id);
}

record Order(UUID id, String status, List<String> lineItems, Instant createdAt) { }

record OrderResponse(UUID id, String status, List<String> lineItems, Instant createdAt) {
    static OrderResponse from(Order order) {
        return new OrderResponse(order.id(), order.status(), List.copyOf(order.lineItems()), order.createdAt());
    }
}
