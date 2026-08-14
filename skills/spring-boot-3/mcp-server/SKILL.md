---
name: mcp-server
description: >
  Use when exposing Spring Boot 3 application capabilities through Model Context Protocol tools,
  resources, or prompts. Covers Spring AI 1.x tool callback registration, transports, schemas,
  errors, security, and standalone MCP Java SDK compatibility.
---

# MCP Server - Spring Boot 3

Prefer the Spring AI MCP server starters in a Spring Boot application. Let the Spring AI BOM manage
its MCP SDK dependency; do not force the standalone SDK version into a starter-based application.

## Dependencies

```xml
<!-- Pick exactly one transport starter. -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server</artifactId>
</dependency>
<!-- Remote MVC: spring-ai-starter-mcp-server-webmvc -->
<!-- Remote reactive: spring-ai-starter-mcp-server-webflux -->
```

For a raw SDK application without Spring AI, use the current MCP Java SDK 2.x release and follow its
migration guide. Do not assume raw SDK examples match the version managed by Spring AI 1.x.

```xml
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>2.0.0</version>
</dependency>
```

## Spring AI 1.x tool callback integration

Spring AI 1.x commonly exposes model `@Tool` methods through an explicit
`MethodToolCallbackProvider`. Keep this callback conversion path separate from native MCP
annotations used by Spring AI 2.0.

```java
@Configuration
class McpToolsConfiguration {
    @Bean
    ToolCallbackProvider orderTools(OrderMcpTools tools) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(tools)
            .build();
    }
}

@Component
final class OrderMcpTools {
    private final OrderService orderService;

    OrderMcpTools(OrderService orderService) {
        this.orderService = orderService;
    }

    @Tool(description = "Get an order by UUID with line items and status history")
    OrderResponse getOrder(
            @ToolParam(description = "Order UUID") String orderId) {
        return OrderResponse.from(orderService.findById(UUID.fromString(orderId)));
    }
}
```

- Return DTOs rather than serialized JPA entities.
- Register each tool object once; duplicate providers create duplicate capabilities.
- Keep tool names and descriptions stable because clients and models depend on them.
- Bound result size, execution time, and downstream calls.
- For raw SDK code, use builders such as `CallToolResult.builder()`; old constructors were removed.

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
```

Use stdio when a local client launches the jar. Use the matching MVC or WebFlux starter and
Streamable HTTP for a remote server when supported by the selected Spring AI 1.x release. Keep
stdout clean in stdio mode because it carries JSON-RPC frames.

## Security and verification

- Authenticate and authorize remote MCP endpoints and every underlying domain operation.
- Validate identifiers, enum values, pagination, and result limits.
- Do not expose secrets, stack traces, persistence entities, or unrestricted administrative tools.
- Test discovery, invalid arguments, authorization, timeouts, and shutdown with a real MCP client.
- Review the selected Spring AI 1.x release notes before copying 2.0 annotation examples.

## Examples

- See `examples/OrderMcpTools.java`, `examples/good-order-tools.java`, and `examples/bad-order-tools.java`.

## Official sources

- Spring AI MCP overview: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html
- MCP Java SDK releases: https://github.com/modelcontextprotocol/java-sdk/releases
- MCP Java SDK 2.0 migration: https://github.com/modelcontextprotocol/java-sdk/blob/main/MIGRATION-2.0.md

## Gotchas

- Agent overrides the MCP SDK under Spring AI 1.x - let the Spring AI BOM manage it.
- Agent copies Spring AI 2.0 native annotation code into a 1.x project - use the selected release's supported registration path.
- Agent uses removed `new CallToolResult(...)` constructors in raw SDK code - use builders.
- Agent logs to stdout in stdio mode - route logs to stderr or a file and disable the banner.
- Agent registers the same tool object twice - keep one callback provider per capability.
- Agent exposes entities or unbounded collections - return bounded DTO contracts.
