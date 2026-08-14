---
name: ai-observability
description: >
  Use when adding Spring AI-specific model observations, token usage, latency, externally
  configured cost attribution, advisor telemetry, or protected prompt and completion logging.
  Use production-observability for general service metrics, health, logs, and OTLP setup.
---

# AI Observability

## Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

## Spring AI Built-in Observability

Spring AI 1.0+ includes built-in Micrometer instrumentation:

```yaml
spring:
  ai:
    chat:
      observations:
        log-prompt: true       # GA renamed include-prompt → log-prompt. OFF in prod (PII).
        log-completion: true   # GA renamed include-completion → log-completion
management:
  metrics:
    tags:
      application: order-service
  endpoints:
    web:
      exposure:
        include: health,prometheus,metrics
```

Auto-generated metrics (OpenTelemetry GenAI semantic conventions):
- `gen_ai.client.operation` — model call latency, tagged with provider and model
- `gen_ai.client.token.usage` — token counts (input/output/total)
- `spring.ai.chat.client` — ChatClient-level operation timer/span

## Custom AI Metrics

```java
@Component
@RequiredArgsConstructor
public class AiMetrics {

    private final MeterRegistry meterRegistry;

    private final Timer.Builder promptTimer = Timer.builder("ai.prompt.latency")
        .description("LLM prompt latency");

    private final Counter.Builder tokenCounter = Counter.builder("ai.tokens.used")
        .description("Total tokens consumed");

    public <T> T track(String operation, String model, Supplier<T> call) {
        return Timer.builder("ai.prompt.latency")
            .tag("operation", operation)
            .tag("model", model)
            .register(meterRegistry)
            .recordCallable(() -> call.get());
    }

    public void recordTokens(String operation, String model, int inputTokens, int outputTokens) {
        Counter.builder("ai.tokens.used")
            .tag("operation", operation)
            .tag("model", model)
            .tag("type", "input")
            .register(meterRegistry)
            .increment(inputTokens);

        Counter.builder("ai.tokens.used")
            .tag("operation", operation)
            .tag("model", model)
            .tag("type", "output")
            .register(meterRegistry)
            .increment(outputTokens);
    }
}
```

## Prompt/Response Logging Advisor

GA replaced the whole advisor API: `CallAroundAdvisor` → `CallAdvisor`, `AdvisedRequest` →
`ChatClientRequest`, `AdvisedResponse` → `ChatClientResponse`, and `Usage.getGenerationTokens()` →
`getCompletionTokens()`. Agents reliably generate the old one — it does not compile on 1.0.

```java
@Component
public class AiAuditAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(AiAuditAdvisor.class);

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String requestId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();

        log.info("[AI-AUDIT] requestId={} promptLength={}",
            requestId, request.prompt().getUserMessage().getText().length());

        try {
            ChatClientResponse response = chain.nextCall(request);
            long latency = System.currentTimeMillis() - start;

            ChatResponse chatResponse = response.chatResponse();
            if (chatResponse != null && chatResponse.getMetadata() != null) {
                Usage usage = chatResponse.getMetadata().getUsage();
                log.info("[AI-AUDIT] requestId={} latencyMs={} inputTokens={} outputTokens={}",
                    requestId, latency,
                    usage.getPromptTokens(), usage.getCompletionTokens()); // GA: not getGenerationTokens()
            }
            return response;
        } catch (Exception e) {
            log.error("[AI-AUDIT] requestId={} FAILED after {}ms", requestId,
                System.currentTimeMillis() - start, e);
            throw e;
        }
    }

    @Override
    public String getName() { return "AiAuditAdvisor"; }

    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}
```

## Cost attribution

- Keep provider prices in externally managed configuration with an effective date and currency.
- Key prices by the exact provider model identifier returned in usage metadata.
- Reject an unknown model instead of silently applying a default price.
- Preserve the raw token usage so historical costs can be recalculated after pricing changes.
- Prefer provider billing exports for invoices; application estimates are operational signals only.

## Structured AI Audit Log (DB)

```java
@Entity
@Table(name = "ai_audit_log")
public class AiAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String operation;
    private String model;
    private int inputTokens;
    private int outputTokens;
    private double estimatedCostUsd;
    private long latencyMs;
    private boolean success;
    private Instant createdAt;
}

// Async to avoid blocking main flow
@Async
public void saveAuditLog(AiAuditLog log) {
    auditLogRepository.save(log);
}
```

## application.yml — Full Observability

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,metrics,info
  metrics:
    distribution:
      percentiles-histogram:
        ai.prompt.latency: true  # enables P50/P95/P99
  tracing:
    sampling:
      probability: 1.0  # 100% trace sampling in dev, reduce in prod

logging:
  level:
    org.springframework.ai: DEBUG  # enable in dev only
```

## Gotchas
- Agent implements `CallAroundAdvisor`/`AdvisedRequest` — removed in GA; use `CallAdvisor`/`ChatClientRequest`
- Agent calls `usage.getGenerationTokens()` — GA renamed it to `getCompletionTokens()`
- Agent logs full prompts in production — keep `log-prompt: false` for PII safety
- Agent skips async on audit saves — always `@Async` to avoid latency impact, and put the `@Async` method on a **separate bean**; calling it on `this` bypasses the proxy and runs synchronously
- Agent hardcodes token pricing — extract to config, prices change
- Agent misses failed calls in metrics — track errors separately with error tag
