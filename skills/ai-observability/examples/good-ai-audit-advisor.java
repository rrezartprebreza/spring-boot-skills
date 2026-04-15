// ✅ GOOD — CallAroundAdvisor, Micrometer metrics, async audit, no PII in logs

@Component
@RequiredArgsConstructor
@Slf4j
public class AiAuditAdvisor implements CallAroundAdvisor {

    private final MeterRegistry meterRegistry;
    private final AuditLogRepository auditLogRepository;

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public String getName() {
        return "AiAuditAdvisor";
    }

    @Override
    public ChatResponse aroundCall(ChatClientRequest request, CallAroundAdvisorChain chain) {
        String requestId = UUID.randomUUID().toString();
        String model = request.chatOptions().getModel();
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            ChatResponse response = chain.nextAroundCall(request);

            // Record metrics
            sample.stop(Timer.builder("ai.request.duration")
                .tag("model", model)
                .tag("status", "success")
                .register(meterRegistry));

            // Track token usage from response metadata
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                meterRegistry.counter("ai.tokens.input", "model", model)
                    .increment(usage.getPromptTokens());
                meterRegistry.counter("ai.tokens.output", "model", model)
                    .increment(usage.getGenerationTokens());
            }

            // Async audit — don't block the main flow
            persistAuditAsync(requestId, model, "SUCCESS", null);

            // Log without PII — only metadata
            log.info("AI request {} completed: model={}", requestId, model);

            return response;
        } catch (Exception e) {
            sample.stop(Timer.builder("ai.request.duration")
                .tag("model", model)
                .tag("status", "error")
                .register(meterRegistry));

            meterRegistry.counter("ai.request.errors", "model", model).increment();
            persistAuditAsync(requestId, model, "ERROR", e.getMessage());

            throw e;
        }
    }

    @Async
    void persistAuditAsync(String requestId, String model, String status, String errorMessage) {
        auditLogRepository.save(new AiAuditLog(requestId, model, status, errorMessage, Instant.now()));
    }
}
