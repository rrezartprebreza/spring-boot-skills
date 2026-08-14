---
name: mcp-server
description: >
  Use when exposing Spring Boot 4 application capabilities through Model Context Protocol tools,
  resources, or prompts. Covers Spring AI 2.0 MCP annotations, transport selection, schemas,
  errors, security, and the standalone MCP Java SDK 2.x.
---

# MCP Server - Spring AI 2.0 and Java SDK 2.x

Prefer Spring AI's MCP server starters and native annotations in Spring Boot applications. Use the
standalone SDK only when Spring integration is intentionally not required.

## Dependencies

Let the Spring AI BOM manage every Spring AI and MCP transitive dependency. Do not override its MCP
SDK version independently.

```xml
<!-- Pick exactly one transport starter. -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server</artifactId>
</dependency>
<!-- Remote MVC: spring-ai-starter-mcp-server-webmvc -->
<!-- Remote reactive: spring-ai-starter-mcp-server-webflux -->
```

For an application that uses the raw SDK without Spring AI, use the current 2.x release and follow
its 2.0 migration guide. MCP Java SDK 2.0 tracks the 2025-11-25 protocol and prefers Streamable HTTP;
SSE transports are deprecated.

```xml
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>2.0.0</version>
</dependency>
```

## Native Spring AI MCP tools

Use `@McpTool` and `@McpToolParam` for server capabilities. `@Tool` is Spring AI's model
tool-calling API; it is not the native MCP server annotation.

```java
@Component
final class OrderMcpTools {
    private final OrderService orderService;

    OrderMcpTools(OrderService orderService) {
        this.orderService = orderService;
    }

    @McpTool(
        name = "get_order",
        description = "Get an order by UUID with line items and status history",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true))
    OrderResponse getOrder(
            @McpToolParam(description = "Order UUID", required = true) String orderId) {
        return OrderResponse.from(orderService.findById(UUID.fromString(orderId)));
    }
}
```

- Return DTOs or records, not persistence entities.
- Set tool hints accurately; clients must still treat them as untrusted metadata.
- Use stable, specific descriptions because the model uses them to select tools.
- Align return types with server mode: synchronous methods for `SYNC`, reactive types for `ASYNC`.
- For expected failures, return a stable structured result. Do not expose stack traces or secrets.
- Use MCP SDK builders such as `CallToolResult.builder()`; legacy result constructors were removed.

## Transport configuration

```yaml
spring:
  main:
    banner-mode: "off"
  ai:
    mcp:
      server:
        name: order-service-mcp
        version: 1.0.0
        type: SYNC
        stdio: true
        annotation-scanner:
          enabled: true
```

For a remote server, select the MVC or WebFlux starter and set
`spring.ai.mcp.server.protocol=STREAMABLE` or `STATELESS`. Use SSE only for compatibility with an
older client. A stdio server must keep stdout free of banners, logs, and `System.out` output because
stdout carries JSON-RPC frames.

## Security and operations

- Authenticate and authorize remote MCP endpoints like any other privileged application API.
- Validate tool arguments and apply domain authorization inside every capability.
- Keep destructive tools narrow and require explicit business preconditions.
- Bound execution time, result size, pagination, and downstream fan-out.
- Log tool name, outcome, duration, and authenticated actor without recording secrets or full data.
- Test initialization, discovery, invalid arguments, authorization, cancellation, and shutdown with a real MCP client.

## Examples

- See `examples/OrderMcpTools.java`, `examples/good-order-tools.java`, and `examples/bad-order-tools.java`.

## Official sources

- Spring AI MCP overview: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html
- Spring AI server annotations: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html
- MCP Java SDK releases: https://github.com/modelcontextprotocol/java-sdk/releases
- MCP Java SDK 2.0 migration: https://github.com/modelcontextprotocol/java-sdk/blob/main/MIGRATION-2.0.md

## Gotchas

- Agent uses `@Tool` for native server registration - use `@McpTool` and `@McpToolParam`.
- Agent overrides the MCP SDK under a Spring AI starter - let the Spring AI BOM manage it.
- Agent uses removed `new CallToolResult(...)` constructors - use `CallToolResult.builder()`.
- Agent configures SSE for a new remote server - prefer Streamable HTTP.
- Agent logs to stdout in stdio mode - route logs to stderr or a file and disable the banner.
- Agent mixes synchronous methods with an asynchronous server - the annotation scanner filters mismatched methods.
- Agent exposes entities or unbounded collections - return bounded DTO contracts.
- Agent trusts tool hints as authorization - enforce authorization in application code.
