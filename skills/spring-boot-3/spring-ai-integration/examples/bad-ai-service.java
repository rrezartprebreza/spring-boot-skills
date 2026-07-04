// ❌ BAD — hardcoded key, string concatenation, inline prompts, no error handling

@Service
public class OrderAnalysisService {

    // ❌ Hardcoded API key in source code — should be in environment variable
    private static final String API_KEY = "sk-ant-abc123...";

    @Autowired
    private ChatClient.Builder chatClientBuilder;         // builds new client per call — wasteful

    public String analyzeOrder(Order order) {
        ChatClient client = chatClientBuilder.build();    // should be injected as singleton

        // ❌ String concatenation for prompt — use .param() with templates
        String prompt = "Analyze this order: " +
            "Customer: " + order.getCustomerEmail() + ", " +
            "Items: " + order.getItems() + ", " +
            "Total: " + order.getTotal();

        // ❌ Inline prompt with no externalization
        String response = client.prompt()
            .user(prompt)                                 // raw concatenated string
            .call()
            .content();                                   // returns raw String, not structured

        // ❌ Manual JSON parsing instead of .entity()
        try {
            ObjectMapper mapper = new ObjectMapper();     // should be injected, not created
            return mapper.readTree(response)
                .get("analysis").asText();
        } catch (Exception e) {
            return "Error";                               // swallows exception, no logging
        }
    }

    // ❌ No Flux/streaming for long responses — blocks thread
    // ❌ No observability — can't track token usage or latency
    // ❌ No fallback behavior when AI service is unavailable
}
