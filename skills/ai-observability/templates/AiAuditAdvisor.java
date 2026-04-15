package com.example.ai.observability;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Production audit advisor for Spring AI.
 * Tracks latency, token usage, and errors without logging PII.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAuditAdvisor implements CallAroundAdvisor {

    private final AiMetrics metrics;
    private final AiAuditLogRepository auditRepository;

    @Override
    public int getOrder() {
        return 0; // runs first in advisor chain
    }

    @Override
    public String getName() {
        return "AiAuditAdvisor";
    }

    @Override
    public ChatResponse aroundCall(ChatClientRequest request, CallAroundAdvisorChain chain) {
        String requestId = UUID.randomUUID().toString();
        String model = request.chatOptions() != null ? request.chatOptions().getModel() : "unknown";
        Instant startTime = Instant.now();

        try {
            ChatResponse response = metrics.track("chat", model, () -> chain.nextAroundCall(request));

            // Extract token usage if available
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                long inputTokens = usage.getPromptTokens();
                long outputTokens = usage.getGenerationTokens();
                metrics.recordTokens(model, inputTokens, outputTokens);

                // Log metadata only — never log prompt content (PII risk)
                log.info("AI request completed: requestId={}, model={}, inputTokens={}, outputTokens={}",
                    requestId, model, inputTokens, outputTokens);
            }

            // Persist audit asynchronously — don't block the response
            persistAuditAsync(requestId, model, "SUCCESS", null, startTime);

            return response;
        } catch (Exception e) {
            log.error("AI request failed: requestId={}, model={}", requestId, model, e);
            persistAuditAsync(requestId, model, "ERROR", e.getMessage(), startTime);
            throw e;
        }
    }

    @Async
    void persistAuditAsync(String requestId, String model, String status,
                           String errorMessage, Instant startTime) {
        try {
            auditRepository.save(new AiAuditLog(
                UUID.fromString(requestId),
                model,
                status,
                errorMessage,
                startTime,
                Instant.now()
            ));
        } catch (Exception e) {
            log.warn("Failed to persist AI audit log: {}", requestId, e);
        }
    }
}
