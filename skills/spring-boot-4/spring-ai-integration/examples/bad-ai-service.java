// BAD — hardcoded key, string concatenation, inline prompts, no error handling

@Service
public class OrderAnalysisService {

    // BAD: Hardcoded API key in source code — should be in environment variable
    private static final String API_KEY = "sk-ant-abc123...";

    @Autowired
    private ChatClient.Builder chatClientBuilder;         // builds new client per call — wasteful

    public String analyzeOrder(Order order) {
        ChatClient client = chatClientBuilder.build();    // should be injected as singleton

        // BAD: String concatenation for prompt — use .param() with templates
        String prompt = "Analyze this order: " +
            "Customer: " + order.getCustomerEmail() + ", " +
            "Items: " + order.getItems() + ", " +
            "Total: " + order.getTotal();

        // BAD: Inline prompt with no externalization
        String response = client.prompt()
            .user(prompt)                                 // raw concatenated string
            .call()
            .content();                                   // returns raw String, not structured

        // BAD: Manual JSON parsing instead of .entity()
        try {
            JsonMapper mapper = JsonMapper.builder().build(); // should be injected — Boot 4 exposes a Jackson 3 JsonMapper bean
            return mapper.readTree(response)
                .get("analysis").asText();
        } catch (Exception e) {
            return "Error";                               // swallows exception, no logging
        }
    }

    // BAD: No Flux/streaming for long responses — blocks thread
    // BAD: No observability — can't track token usage or latency
    // BAD: No fallback behavior when AI service is unavailable
}
