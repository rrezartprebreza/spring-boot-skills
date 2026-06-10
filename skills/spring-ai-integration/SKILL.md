---
name: spring-ai-integration
description: >
  Use when integrating LLMs, chat clients, embeddings, RAG pipelines, or AI agents into
  Spring Boot. Covers Spring AI ChatClient, prompt templates, embeddings, vector stores,
  and structured output. Use when user mentions Spring AI, LLM, ChatGPT, Claude, RAG, embeddings.
---

# Spring AI Integration

## Dependencies

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Choose your model provider — 1.0 GA renamed every starter to spring-ai-starter-* -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-anthropic</artifactId>
    </dependency>
    <!-- OR -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>

    <!-- For RAG / vector search -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
    </dependency>
</dependencies>
```

> **Watch the artifact names.** 1.0 GA dropped the old `spring-ai-<x>-spring-boot-starter`
> coordinates. The pattern is now `spring-ai-starter-model-<provider>` (e.g. `-model-anthropic`,
> `-model-openai`) and `spring-ai-starter-vector-store-<store>`. Agents trained on pre-GA Spring AI
> will emit the dead names — they resolve to nothing in Maven Central.

## ChatClient — Basic Usage

```java
@Service
@RequiredArgsConstructor
public class DocumentSummaryService {

    private final ChatClient chatClient;

    public String summarize(String content) {
        return chatClient.prompt()
            .user(u -> u.text("Summarize the following document in 3 bullet points:\n\n{content}")
                .param("content", content))
            .call()
            .content();
    }

    // With system prompt
    public String analyzeFinancial(String document, String language) {
        return chatClient.prompt()
            .system("You are a financial analyst. Respond in {language}.")
            .system(s -> s.param("language", language))
            .user(document)
            .call()
            .content();
    }
}
```

## ChatClient Bean Configuration

```java
@Configuration
public class AiConfig {

    @Bean
    public ChatMemory chatMemory() {
        // 1.0 GA: InMemoryChatMemory is GONE. Use MessageWindowChatMemory —
        // it caps history to a sliding window and defaults to an in-memory repository.
        return MessageWindowChatMemory.builder()
            .maxMessages(20)
            .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
            .defaultSystem("You are a helpful assistant for an e-commerce platform.")
            .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build(), // GA: builder, not new(...)
                new SimpleLoggerAdvisor() // logs prompts/responses
            )
            .build();
    }
}
```

## Prompt Templates (externalized)

```java
// src/main/resources/prompts/analyze-order.st
// Analyze this order and identify any anomalies:
// Customer: {customer}
// Items: {items}
// Total: {total}
// Flag any unusual patterns.

@Service
public class OrderAnalysisService {

    @Value("classpath:prompts/analyze-order.st")
    private Resource promptTemplate;

    public String analyzeOrder(Order order) {
        return chatClient.prompt()
            .user(u -> u.text(promptTemplate)
                .param("customer", order.getCustomerEmail())
                .param("items", order.getItems().toString())
                .param("total", order.getTotal()))
            .call()
            .content();
    }
}
```

## Structured Output

```java
// Define the target record
public record OrderClassification(
    String category,
    String priority,
    List<String> tags,
    boolean requiresManualReview
) {}

@Service
public class OrderClassifier {

    public OrderClassification classify(String orderDescription) {
        return chatClient.prompt()
            .user("Classify this order: " + orderDescription)
            .call()
            .entity(OrderClassification.class); // Spring AI handles JSON parsing
    }
}
```

## RAG Pipeline

```java
@Configuration
public class RagConfig {

    // No manual VectorStore bean — the spring-ai-starter-vector-store-pgvector
    // starter auto-configures one. Just inject it. (The old `new PgVectorStore(...)`
    // constructor is removed in GA; if you must build one, use PgVectorStore.builder(...).)

    @Bean
    public ChatClient ragChatClient(ChatClient.Builder builder, VectorStore vectorStore) {
        return builder
            .defaultAdvisors(
                QuestionAnswerAdvisor.builder(vectorStore)
                    .searchRequest(SearchRequest.builder().topK(5).build()) // GA: builder, not defaults().withTopK()
                    .build()
            )
            .build();
    }
}

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final VectorStore vectorStore;
    private final ChatClient ragChatClient;

    // Ingest documents
    public void ingest(List<String> documents) {
        List<Document> docs = documents.stream()
            .map(content -> new Document(content))
            .toList();
        vectorStore.add(docs);
    }

    // Query with RAG
    public String ask(String question) {
        return ragChatClient.prompt()
            .user(question)
            .call()
            .content();
    }
}
```

## Streaming Responses

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> stream(@RequestParam String prompt) {
    return chatClient.prompt()
        .user(prompt)
        .stream()
        .content();
}
```

## application.yml

```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-sonnet-4-20250514
          max-tokens: 2048
          temperature: 0.7
    # OR for OpenAI:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o
    vectorstore:
      pgvector:
        initialize-schema: true
        dimensions: 1536
```

## Gotchas
- Agent uses pre-GA artifact names (`spring-ai-anthropic-spring-boot-starter`) — GA is `spring-ai-starter-model-anthropic`
- Agent writes `new MessageChatMemoryAdvisor(new InMemoryChatMemory())` — both removed in GA; use `MessageChatMemoryAdvisor.builder(chatMemory)` + `MessageWindowChatMemory`
- Agent writes `SearchRequest.defaults().withTopK(n)` — GA is `SearchRequest.builder().topK(n).build()`
- Agent hardcodes API keys — always use environment variables / `${...}`
- Agent builds prompts with string concatenation — use `.param()` template variables
- Agent puts prompts inline in code — externalize to `src/main/resources/prompts/`
- Agent ignores structured output — use `.entity(MyClass.class)` instead of parsing manually
- Agent uses `.entity(List.class)` for a list — generics erase; pass `new ParameterizedTypeReference<List<X>>() {}`
- Agent skips error handling for API calls — wrap in try/catch, handle `NonTransientAiException` (don't retry) vs `TransientAiException` (retry)
- Agent forgets a per-user `conversationId` on the memory advisor — all users share one chat history
- Agent uses wrong model string — verify model names against provider docs
