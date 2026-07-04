// ✅ GOOD — GA CallAdvisor API, Micrometer metrics, async audit via separate bean, no PII in logs

@Component
@RequiredArgsConstructor
@Slf4j
public class AiAuditAdvisor implements CallAdvisor {

    private final MeterRegistry meterRegistry;
    private final AiAuditRecorder auditRecorder;   // separate bean — @Async needs the proxy

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public String getName() {
        return "AiAuditAdvisor";
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String requestId = UUID.randomUUID().toString();
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            ChatClientResponse response = chain.nextCall(request);
            ChatResponse chatResponse = response.chatResponse();

            // Record metrics
            sample.stop(Timer.builder("ai.request.duration")
                .tag("status", "success")
                .register(meterRegistry));

            // Track token usage from response metadata
            if (chatResponse != null && chatResponse.getMetadata() != null
                    && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                meterRegistry.counter("ai.tokens.input").increment(usage.getPromptTokens());
                meterRegistry.counter("ai.tokens.output").increment(usage.getCompletionTokens()); // GA name
            }

            // Async audit — separate bean, so the @Async proxy is actually used
            auditRecorder.record(requestId, "SUCCESS", null);

            // Log without PII — only metadata
            log.info("AI request {} completed", requestId);

            return response;
        } catch (Exception e) {
            sample.stop(Timer.builder("ai.request.duration")
                .tag("status", "error")
                .register(meterRegistry));

            meterRegistry.counter("ai.request.errors").increment();
            auditRecorder.record(requestId, "ERROR", e.getMessage());

            throw e;
        }
    }
}

// Separate bean: calling an @Async method on `this` bypasses the Spring proxy and
// runs synchronously — the same self-invocation pitfall as @Transactional.
@Component
@RequiredArgsConstructor
class AiAuditRecorder {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void record(String requestId, String status, String errorMessage) {
        auditLogRepository.save(new AiAuditLog(requestId, status, errorMessage, Instant.now()));
    }
}
