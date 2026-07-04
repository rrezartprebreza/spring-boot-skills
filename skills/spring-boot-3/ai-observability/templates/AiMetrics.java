package com.example.ai.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Centralized AI metrics using Micrometer.
 * Records latency, token usage, errors, and cost estimates.
 */
@Component
@RequiredArgsConstructor
public class AiMetrics {

    private final MeterRegistry registry;

    /**
     * Time an AI operation and record success/failure.
     */
    public <T> T track(String operation, String model, Supplier<T> action) {
        Timer.Sample sample = Timer.start(registry);
        try {
            T result = action.get();
            sample.stop(timer(operation, model, "success"));
            return result;
        } catch (Exception e) {
            sample.stop(timer(operation, model, "error"));
            errorCounter(operation, model).increment();
            throw e;
        }
    }

    /**
     * Record token usage from a completed AI response.
     */
    public void recordTokens(String model, long inputTokens, long outputTokens) {
        counter("ai.tokens", "model", model, "type", "input").increment(inputTokens);
        counter("ai.tokens", "model", model, "type", "output").increment(outputTokens);
        counter("ai.tokens.total", "model", model).increment(inputTokens + outputTokens);
    }

    /**
     * Record estimated cost (pass rate from configuration, not hardcoded).
     */
    public void recordCost(String model, double estimatedCost) {
        registry.counter("ai.cost.estimated", "model", model).increment(estimatedCost);
    }

    private Timer timer(String operation, String model, String status) {
        return Timer.builder("ai.request.duration")
            .tag("operation", operation)
            .tag("model", model)
            .tag("status", status)
            .description("AI request latency")
            .register(registry);
    }

    private Counter errorCounter(String operation, String model) {
        return counter("ai.request.errors", "operation", operation, "model", model);
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }
}
