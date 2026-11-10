// GOOD — injected ChatClient, externalized prompts, structured output, error handling

@Service
@Slf4j
public class OrderAnalysisService {

    // One-shot analysis: a stateless client built from the auto-configured (prototype)
    // ChatClient.Builder. The shared ChatClient bean carries MessageChatMemoryAdvisor,
    // which needs a per-user conversation id — without one, Spring AI 2 rejects the
    // call and 1.x silently pools every user into one shared chat history.
    private final ChatClient chatClient;

    public OrderAnalysisService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    // Externalized prompt template loaded from classpath:prompts/analyze-order.st
    @Value("classpath:prompts/analyze-order.st")
    private Resource analyzePrompt;

    public OrderAnalysis analyzeOrder(Order order) {
        try {
            return chatClient.prompt()
                .user(u -> u
                    .text(analyzePrompt)
                    .param("customerEmail", order.getCustomerEmail())
                    .param("items", order.getItems().toString())
                    .param("total", order.getTotal().toString()))
                .call()
                .entity(OrderAnalysis.class);  // structured output — auto-parsed to record
        } catch (Exception e) {
            log.error("AI analysis failed for order {}", order.getId(), e);
            return OrderAnalysis.fallback("Analysis unavailable");
        }
    }

    // Streaming for long responses
    public Flux<String> streamRecommendations(UUID customerId, List<Order> history) {
        return chatClient.prompt()
            .system("You are a product recommendation engine.")
            .user(u -> u
                .text("Based on order history: {history}, suggest products.")
                .param("history", history.toString()))
            .stream()
            .content();
    }

    public record OrderAnalysis(
        String riskLevel,
        String summary,
        List<String> recommendations
    ) {
        public static OrderAnalysis fallback(String reason) {
            return new OrderAnalysis("UNKNOWN", reason, List.of());
        }
    }
}
