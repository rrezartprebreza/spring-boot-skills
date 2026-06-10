// ❌ BAD — logs full prompts (PII), synchronous audit, hardcoded pricing, no error metrics,
//          @Async via self-invocation (silently runs synchronously)

@Component
@Slf4j
public class AiAuditAdvisor implements CallAdvisor {

    @Autowired                                           // field injection
    private AuditLogRepository auditLogRepository;

    @Override
    public int getOrder() { return 0; }

    @Override
    public String getName() { return "AiAuditAdvisor"; }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {

        // ❌ Logs full prompt content — may contain PII, secrets, customer data
        log.info("AI request prompt: {}", request.prompt().getContents());

        long start = System.currentTimeMillis();
        ChatClientResponse response = chain.nextCall(request);  // no try/catch — errors untracked
        long duration = System.currentTimeMillis() - start;

        // ❌ Hardcoded token pricing — changes when provider updates pricing
        Usage usage = response.chatResponse().getMetadata().getUsage();
        double cost = usage.getPromptTokens() * 0.003 / 1000
            + usage.getCompletionTokens() * 0.015 / 1000;

        // ❌ Synchronous audit save — blocks the main request thread
        // ❌ this.persistAsync(...) would be just as broken: @Async on a method of the
        //    same bean bypasses the proxy and runs inline (self-invocation pitfall)
        auditLogRepository.save(new AiAuditLog(
            request.prompt().getContents(),                              // stores full prompt (PII risk)
            response.chatResponse().getResult().getOutput().getText(),  // stores full response
            cost,
            duration));

        // ❌ No Micrometer metrics — can't alert on latency spikes or error rates
        // ❌ No request ID for tracing
        // ❌ No error handling — if chain throws, no metrics or audit recorded
        // ❌ Only counters, no histograms for latency distribution

        return response;
    }
}
