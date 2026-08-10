@Component
final class OrderMcpTools {
    @Tool(description = "Get an order by UUID")
    OrderResponse getOrder(@ToolParam(description = "Order UUID") String orderId) {
        return OrderResponse.from(orderService.findById(UUID.fromString(orderId)));
    }
}
