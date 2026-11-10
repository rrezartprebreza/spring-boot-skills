package com.example.tasks;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportTasks {
    private final Renderer renderer;

    public ReportTasks(Renderer renderer) {
        this.renderer = renderer;
    }

    @Async("reportExecutor")
    public CompletableFuture<String> render(UUID reportId) {
        return CompletableFuture.completedFuture(renderer.render(reportId));
    }

    @FunctionalInterface
    public interface Renderer {
        String render(UUID reportId);
    }
}
