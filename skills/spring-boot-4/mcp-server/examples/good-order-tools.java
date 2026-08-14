@Component
final class ReadOnlyOrderMcpTools {
    @McpTool(name = "get_order", description = "Get an order by UUID", generateOutputSchema = true)
    OrderResponse getOrder(
            @McpToolParam(description = "Order UUID", required = true) String orderId) {
        return OrderResponse.from(orderService.findById(UUID.fromString(orderId)));
    }
}
