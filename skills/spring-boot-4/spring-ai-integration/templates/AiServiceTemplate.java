package com.example.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceTemplate {

    private final ChatClient chatClient;

    @Value("classpath:prompts/analyze-order.st")
    private Resource analyzePrompt;

    /**
     * Simple prompt with structured output — auto-parsed to Java record.
     */
    public OrderAnalysis analyzeOrder(String customerEmail, String items, String total) {
        try {
            return chatClient.prompt()
                .user(u -> u
                    .text(analyzePrompt)
                    .param("customerEmail", customerEmail)
                    .param("items", items)
                    .param("total", total))
                .call()
                .entity(OrderAnalysis.class);
        } catch (Exception e) {
            log.error("AI analysis failed", e);
            return OrderAnalysis.fallback("Service unavailable");
        }
    }

    /**
     * Streaming response for long-form content.
     */
    public Flux<String> streamAnalysis(String context) {
        return chatClient.prompt()
            .system("You are a business analyst specializing in e-commerce orders.")
            .user(context)
            .stream()
            .content();
    }

    /**
     * Structured list output — Spring AI parses JSON array into List.
     */
    public List<ProductRecommendation> getRecommendations(String orderHistory) {
        try {
            return chatClient.prompt()
                .system("Recommend products based on order history. Return as JSON array.")
                .user(orderHistory)
                .call()
                .entity(new org.springframework.core.ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.error("Recommendation generation failed", e);
            return List.of();
        }
    }

    public record OrderAnalysis(String riskLevel, String summary, List<String> recommendations) {
        public static OrderAnalysis fallback(String reason) {
            return new OrderAnalysis("UNKNOWN", reason, List.of());
        }
    }

    public record ProductRecommendation(String productName, String reason, double confidence) {}
}
