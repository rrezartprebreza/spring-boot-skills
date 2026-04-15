// ❌ BAD — logs full prompts (PII), synchronous audit, hardcoded pricing, no error metrics

@Component
@Slf4j
public class AiAuditAdvisor implements CallAroundAdvisor {

    @Autowired                                           // field injection
    private AuditLogRepository auditLogRepository;

    @Override
    public int getOrder() { return 0; }

    @Override
    public String getName() { return "AiAuditAdvisor"; }

    @Override
    public ChatResponse aroundCall(ChatClientRequest request, CallAroundAdvisorChain chain) {

        // ❌ Logs full prompt content — may contain PII, secrets, customer data
        log.info("AI request prompt: {}", request.prompt().getContents());

        long start = System.currentTimeMillis();
        ChatResponse response = chain.nextAroundCall(request);  // no try/catch — errors untracked
        long duration = System.currentTimeMillis() - start;

        // ❌ Hardcoded token pricing — changes when provider updates pricing
        double cost = response.getMetadata().getUsage().getPromptTokens() * 0.003 / 1000
            + response.getMetadata().getUsage().getGenerationTokens() * 0.015 / 1000;

        // ❌ Synchronous audit save — blocks the main request thread
        auditLogRepository.save(new AiAuditLog(
            request.prompt().getContents(),               // stores full prompt (PII risk)
            response.getResult().getOutput().getContent(), // stores full response
            cost,
            duration));

        // ❌ No Micrometer metrics — can't alert on latency spikes or error rates
        // ❌ No request ID for tracing
        // ❌ No error handling — if chain throws, no metrics or audit recorded
        // ❌ Only counters, no histograms for latency distribution

        return response;
    }
}
