---
name: ai-observability
description: >
  Use when adding monitoring, metrics, logging, or tracing to Spring AI or LLM integration
  code. Covers token tracking, latency measurement, cost estimation, and prompt/response
  logging. Use when user mentions AI monitoring, token costs, or LLM observability.
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
        include-prompt: true       # log prompt content (disable in prod for PII)
        include-completion: true   # log response content
management:
  metrics:
    tags:
      application: order-service
  endpoints:
    web:
      exposure:
        include: health,prometheus,metrics
```

Auto-generated metrics:
- `spring.ai.chat.client.operation.seconds` — latency histogram
- `spring.ai.chat.client.token.usage` — token counts (input/output/total)

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

```java
@Component
public class AiAuditAdvisor implements CallAroundAdvisor {

    private static final Logger log = LoggerFactory.getLogger(AiAuditAdvisor.class);

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest request, CallAroundAdvisorChain chain) {
        String requestId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();

        log.info("[AI-AUDIT] requestId={} model={} promptLength={}",
            requestId,
            request.chatOptions() != null ? request.chatOptions().getModel() : "default",
            request.userText().length());

        try {
            AdvisedResponse response = chain.nextAroundCall(request);
            long latency = System.currentTimeMillis() - start;

            ChatResponse chatResponse = response.response();
            if (chatResponse != null && chatResponse.getMetadata() != null) {
                Usage usage = chatResponse.getMetadata().getUsage();
                log.info("[AI-AUDIT] requestId={} latencyMs={} inputTokens={} outputTokens={}",
                    requestId, latency,
                    usage.getPromptTokens(), usage.getGenerationTokens());
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

## Cost Estimation

```java
@Service
public class AiCostEstimator {

    // Prices per million tokens — update when pricing changes
    private static final Map<String, double[]> PRICING = Map.of(
        "claude-sonnet-4-20250514", new double[]{3.0, 15.0},  // [input, output] per 1M tokens
        "claude-haiku-4-5-20251001", new double[]{0.8, 4.0},
        "gpt-4o", new double[]{5.0, 15.0},
        "gpt-4o-mini", new double[]{0.15, 0.6}
    );

    public double estimateCost(String model, int inputTokens, int outputTokens) {
        double[] prices = PRICING.getOrDefault(model, new double[]{5.0, 15.0});
        return (inputTokens * prices[0] + outputTokens * prices[1]) / 1_000_000;
    }
}
```

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
- Agent logs full prompts in production — disable with `include-prompt: false` for PII safety
- Agent skips async on audit saves — always `@Async` to avoid latency impact
- Agent hardcodes token pricing — extract to config, prices change
- Agent misses failed calls in metrics — track errors separately with error tag
