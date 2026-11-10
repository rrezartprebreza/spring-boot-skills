package com.example.ai.observability;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Production audit advisor for Spring AI 2.0.
 * Tracks latency, token usage, and errors without logging PII.
 *
 * API notes:
 * - CallAroundAdvisor / AdvisedRequest / AdvisedResponse are pre-GA APIs.
 *   The current interface is CallAdvisor with adviseCall(ChatClientRequest, CallAdvisorChain).
 * - Use Usage.getCompletionTokens(); do not call the old getGenerationTokens().
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAuditAdvisor implements CallAdvisor {

    private final AiMetrics metrics;
    private final AiAuditRecorder auditRecorder;   // separate bean — see note on @Async below

    @Override
    public int getOrder() {
        return 0; // runs first in advisor chain
    }

    @Override
    public String getName() {
        return "AiAuditAdvisor";
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String requestId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        String requestedModel = requestedModel(request);

        try {
            ChatClientResponse response = metrics.track("chat", requestedModel, () -> chain.nextCall(request));
            ChatResponse chatResponse = response.chatResponse();

            // Extract token usage if available
            if (chatResponse != null && chatResponse.getMetadata() != null
                    && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                long inputTokens = usage.getPromptTokens();
                long outputTokens = usage.getCompletionTokens();
                // The response reports the model that actually served the call.
                String servedModel = chatResponse.getMetadata().getModel();
                metrics.recordTokens(servedModel != null ? servedModel : requestedModel, inputTokens, outputTokens);

                // Log metadata only — never log prompt content (PII risk)
                log.info("AI request completed: requestId={}, inputTokens={}, outputTokens={}",
                    requestId, inputTokens, outputTokens);
            }

            // Persist audit asynchronously — don't block the response
            recordBestEffort(requestId, "SUCCESS", null, startTime);

            return response;
        } catch (Exception e) {
            log.error("AI request failed: requestId={}, failureType={}", requestId, e.getClass().getSimpleName());
            recordBestEffort(requestId, "ERROR", e.getClass().getSimpleName(), startTime);
            throw e;
        }
    }

    private void recordBestEffort(String requestId, String status, String errorType, Instant startTime) {
        try {
            auditRecorder.record(requestId, status, errorType, startTime);
        } catch (RuntimeException failure) {
            // Submission can fail before the @Async method body runs.
            log.warn("AI audit submission failed: requestId={}, failureType={}",
                requestId, failure.getClass().getSimpleName());
        }
    }

    private static String requestedModel(ChatClientRequest request) {
        ChatOptions options = request.prompt().getOptions();
        return options != null && options.getModel() != null ? options.getModel() : "default";
    }
}

/**
 * Async persistence lives in its OWN bean on purpose: an @Async method invoked on
 * `this` bypasses the Spring proxy and runs synchronously (self-invocation pitfall,
 * same as @Transactional). A separate component goes through the proxy.
 */
@Component
@RequiredArgsConstructor
class AiAuditRecorder {

    private final AiAuditLogRepository auditRepository;

    @Async
    public void record(String requestId, String status, String errorMessage, Instant startTime) {
        try {
            auditRepository.save(new AiAuditLog(
                UUID.fromString(requestId),
                status,
                errorMessage,
                startTime,
                Instant.now()
            ));
        } catch (Exception e) {
            // Never let audit failures break the AI call path
            org.slf4j.LoggerFactory.getLogger(AiAuditRecorder.class)
                .warn("Failed to persist AI audit log: requestId={}, failureType={}",
                    requestId, e.getClass().getSimpleName());
        }
    }
}
