package com.example.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

/**
 * MCP tools for Order Service.
 * Drop-in class — Spring AI scans @Tool methods automatically.
 *
 * Usage in .mcp.json:
 * {
 *   "mcpServers": {
 *     "order-service": {
 *       "command": "java",
 *       "args": ["-jar", "order-service.jar", "--spring.profiles.active=mcp"]
 *     }
 *   }
 * }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMcpTools {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @Tool(description = "Get a single order by UUID. Returns full order details including line items, status, and timestamps.")
    public String getOrder(
        @ToolParam(description = "UUID of the order to retrieve") String orderId
    ) {
        try {
            Order order = orderService.findById(UUID.fromString(orderId));
            return objectMapper.writeValueAsString(OrderResponse.from(order));
        } catch (IllegalArgumentException e) {
            return errorJson("INVALID_ID", "Not a valid UUID: " + orderId);
        } catch (EntityNotFoundException e) {
            return errorJson("NOT_FOUND", "Order not found: " + orderId);
        } catch (Exception e) {
            log.error("getOrder failed for id={}", orderId, e);
            return errorJson("INTERNAL_ERROR", "Unexpected error");
        }
    }

    @Tool(description = "List all orders for a customer email. Optionally filter by status: PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED.")
    public String listOrders(
        @ToolParam(description = "Customer email address") String email,
        @ToolParam(description = "Optional status filter", required = false) String status
    ) {
        try {
            var orders = status != null
                ? orderService.findByEmailAndStatus(email, OrderStatus.valueOf(status.toUpperCase()))
                : orderService.findByEmail(email);
            return objectMapper.writeValueAsString(
                orders.stream().map(OrderResponse::from).toList()
            );
        } catch (IllegalArgumentException e) {
            return errorJson("INVALID_STATUS", "Unknown status: " + status +
                ". Valid values: PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED");
        } catch (Exception e) {
            log.error("listOrders failed for email={}", email, e);
            return errorJson("INTERNAL_ERROR", "Unexpected error");
        }
    }

    @Tool(description = "Get order statistics for a customer: total orders, total spend, orders by status.")
    public String getOrderStats(
        @ToolParam(description = "Customer email address") String email
    ) {
        try {
            OrderStats stats = orderService.getStatsByEmail(email);
            return objectMapper.writeValueAsString(stats);
        } catch (Exception e) {
            log.error("getOrderStats failed for email={}", email, e);
            return errorJson("INTERNAL_ERROR", "Unexpected error");
        }
    }

    private String errorJson(String code, String message) {
        return String.format("{\"error\":\"%s\",\"message\":\"%s\"}", code, message);
    }
}
