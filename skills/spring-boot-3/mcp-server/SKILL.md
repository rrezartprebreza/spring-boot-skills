---
name: mcp-server
description: >
  Use when building MCP (Model Context Protocol) servers in Java/Spring Boot. Covers tool
  registration, resource exposure, prompt templates, and production deployment using the
  official MCP Java SDK. Use when user mentions MCP, AI agent integration, or tool calling.
---

# MCP Server — Java SDK

Official Java SDK: https://github.com/modelcontextprotocol/java-sdk  
Maintained by Anthropic in collaboration with Spring AI.

## Dependency

The standalone SDK reached **1.0.0 GA** (`io.modelcontextprotocol.sdk:mcp`). Most Spring Boot
apps should use the **Spring AI MCP starter** instead — it auto-configures the server, transport,
and tool scanning. The starter coordinates were renamed at Spring AI 1.0 GA to `spring-ai-starter-mcp-server-*`.

```xml
<!-- Recommended for Spring Boot: pick ONE transport starter -->
<!-- stdio (Claude Desktop / Claude Code launching the jar locally) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server</artifactId>
</dependency>
<!-- OR remote HTTP (SSE + Streamable-HTTP) over Spring MVC -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
<!-- OR reactive: spring-ai-starter-mcp-server-webflux -->
```

```xml
<!-- Or drive the raw SDK directly (no Spring AI), now at 1.0.0 GA -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>1.0.0</version>
</dependency>
```

> The old `spring-ai-mcp-server-spring-boot-starter` name is dead. GA is `spring-ai-starter-mcp-server`
> (stdio), `-webmvc` (servlet SSE / Streamable-HTTP), and `-webflux` (reactive).

## Minimal MCP Server (stdio transport)

```java
@SpringBootApplication
public class OrderMcpServer {
    public static void main(String[] args) {
        var transport = new StdioServerTransportProvider();

        var server = McpServer.sync(transport)
            .serverInfo("order-service-mcp", "1.0.0")
            .capabilities(ServerCapabilities.builder().tools(true).resources(true).build())
            .tools(getOrderTool(), listOrdersTool())
            .build();

        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
    }
}
```

## Defining Tools

```java
// Tool with typed input/output
private static McpServerFeatures.SyncToolSpecification getOrderTool() {
    var schema = """
        {
          "type": "object",
          "properties": {
            "orderId": { "type": "string", "description": "UUID of the order" }
          },
          "required": ["orderId"]
        }
        """;

    return McpServerFeatures.SyncToolSpecification.builder()
        .tool(Tool.builder()
            .name("get_order")
            .description("Get a single order by ID including all line items and status history")
            .inputSchema(schema)
            .build())
        .callHandler((exchange, args) -> {
            String orderId = (String) args.get("orderId");
            try {
                Order order = orderService.findById(UUID.fromString(orderId));
                return new CallToolResult(List.of(
                    new TextContent(objectMapper.writeValueAsString(order))
                ), false);
            } catch (EntityNotFoundException e) {
                return new CallToolResult(List.of(
                    new TextContent("Order not found: " + orderId)
                ), true); // isError = true
            }
        })
        .build();
}
```

## Spring Boot Integration (recommended)

```java
@Configuration
public class McpToolsConfig {

    @Bean
    public ToolCallbackProvider orderTools(OrderService orderService, ObjectMapper objectMapper) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(new OrderMcpTools(orderService, objectMapper))
            .build();
    }
}

@Component
public class OrderMcpTools {
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    // Spring AI annotation-based tool registration
    @Tool(description = "Get order by ID with full line items and status history")
    public String getOrder(@ToolParam(description = "UUID of the order") String orderId) {
        try {
            Order order = orderService.findById(UUID.fromString(orderId));
            return objectMapper.writeValueAsString(OrderResponse.from(order));
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "List orders for a customer, optionally filtered by status")
    public String listOrders(
        @ToolParam(description = "Customer email address") String email,
        @ToolParam(description = "Filter by status: PENDING, PROCESSING, SHIPPED, DELIVERED", required = false) String status
    ) {
        List<Order> orders = status != null
            ? orderService.findByEmailAndStatus(email, OrderStatus.valueOf(status))
            : orderService.findByEmail(email);
        return objectMapper.writeValueAsString(orders.stream().map(OrderResponse::from).toList());
    }
}
```

## application.yml for MCP Server

```yaml
spring:
  ai:
    mcp:
      server:
        name: order-service-mcp
        version: 1.0.0
        type: SYNC          # SYNC (blocking) or ASYNC (reactive / WebFlux)
        # --- stdio: needs spring-ai-starter-mcp-server + banner/console logging OFF ---
        stdio: true         # framing is over stdin/stdout — nothing else may write there
        # --- remote: needs the -webmvc or -webflux starter instead ---
        # protocol: STREAMABLE   # SSE | STREAMABLE | STATELESS (Streamable-HTTP is the 2025-06-18 default)
```

> **stdio servers must keep stdout clean.** Any log line, banner, or `System.out.println` corrupts
> the JSON-RPC framing and the client silently drops the connection. For stdio, set
> `spring.main.banner-mode=off` and route logging to a file or stderr.

## Exposing Resources

```java
@Bean
public List<McpServerFeatures.SyncResourceSpecification> mcpResources(OrderRepository repo) {
    return List.of(
        McpServerFeatures.SyncResourceSpecification.builder()
            .resource(Resource.builder()
                .uri("orders://recent")
                .name("Recent Orders")
                .description("Last 50 orders across all customers")
                .mimeType("application/json")
                .build())
            .readHandler((exchange, request) -> {
                List<Order> recent = repo.findTop50ByOrderByCreatedAtDesc();
                return new ReadResourceResult(List.of(
                    new TextResourceContents(request.uri(),
                        objectMapper.writeValueAsString(recent), "application/json")
                ));
            })
            .build()
    );
}
```

## claude_desktop_config.json / .mcp.json

```json
{
  "mcpServers": {
    "order-service": {
      "command": "java",
      "args": ["-jar", "/path/to/order-mcp-server.jar"],
      "env": {
        "SPRING_DATASOURCE_URL": "jdbc:postgresql://localhost:5432/orders"
      }
    }
  }
}
```

## Error Handling Pattern

```java
// Always return structured errors — never throw from tool handlers
private CallToolResult safeExecute(Supplier<Object> action) {
    try {
        return new CallToolResult(
            List.of(new TextContent(objectMapper.writeValueAsString(action.get()))),
            false
        );
    } catch (EntityNotFoundException e) {
        return errorResult("NOT_FOUND", e.getMessage());
    } catch (Exception e) {
        log.error("Tool execution failed", e);
        return errorResult("INTERNAL_ERROR", "Unexpected error occurred");
    }
}

private CallToolResult errorResult(String code, String message) {
    return new CallToolResult(
        List.of(new TextContent(String.format("{\"error\":\"%s\",\"message\":\"%s\"}", code, message))),
        true // isError flag — agent knows this is an error
    );
}
```

## Gotchas
- Agent generates Python MCP code — always use the Java SDK
- Agent uses the dead `spring-ai-mcp-server-spring-boot-starter` name — GA is `spring-ai-starter-mcp-server[-webmvc|-webflux]`
- Agent pins SDK `0.9.0` — the standalone SDK is `1.0.0` GA (or just use the Spring AI starter)
- Agent logs to stdout on a stdio server — corrupts JSON-RPC framing; banner off, logs to file/stderr
- Agent forgets `isError = true` in error results — agent can't distinguish errors from data
- Agent uses `FetchType.EAGER` inside tool handlers — triggers N+1, use projections
- Agent exposes entities directly — serialize to DTOs before returning
- Agent ignores `shutdown hooks` — always close the server on JVM shutdown
- Agent writes vague tool descriptions — the description IS the prompt the model reads; be specific about when to call it and what it returns
- `stdio` for local tools (Claude Code, Claude Desktop); `-webmvc`/`-webflux` + Streamable-HTTP for remote
