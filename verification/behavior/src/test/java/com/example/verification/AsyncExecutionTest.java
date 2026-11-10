package com.example.verification;

import com.example.tasks.ReportExecutorConfiguration;
import com.example.tasks.ReportTasks;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;

class AsyncExecutionTest {
    private AnnotationConfigApplicationContext context(ReportTasks.Renderer renderer) {
        var context = new AnnotationConfigApplicationContext();
        context.register(ReportExecutorConfiguration.class);
        context.registerBean(ReportTasks.Renderer.class, () -> renderer);
        context.registerBean(ReportTasks.class);
        context.refresh();
        return context;
    }

    @Test void proxyUsesNamedWorkerWithoutInheritingCallerTransaction() throws Exception {
        try (var context = context(id -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return Thread.currentThread().getName();
        })) {
            // Mark only this thread: this checks thread-bound context, not database commit visibility.
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                var result = context.getBean(ReportTasks.class).render(UUID.randomUUID());
                assertThat(result.get(5, TimeUnit.SECONDS)).startsWith("report-");
            } finally {
                TransactionSynchronizationManager.clear();
            }
        }
    }

    @Test void workerFailureReachesTheReturnedFuture() {
        try (var context = context(id -> { throw new IllegalStateException("render failed"); })) {
            var result = context.getBean(ReportTasks.class).render(UUID.randomUUID());
            assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
        }
    }

    @Test void saturatedExecutorRejectsSubmissionAndRecovers() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(4);
        try (var context = context(id -> "done")) {
            ThreadPoolTaskExecutor executor = context.getBean("reportExecutor", ThreadPoolTaskExecutor.class);
            try {
                for (int i = 0; i < 36; i++) executor.execute(() -> {
                    started.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test gate timed out");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(executor.getThreadPoolExecutor().getQueue()).hasSize(32);
                assertThatThrownBy(() -> context.getBean(ReportTasks.class).render(UUID.randomUUID()))
                        .isInstanceOf(TaskRejectedException.class);
            } finally {
                release.countDown();
            }
            org.awaitility.Awaitility.await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> executor.getActiveCount() == 0 && executor.getQueueSize() == 0);
            assertThat(context.getBean(ReportTasks.class).render(UUID.randomUUID()).get(5, TimeUnit.SECONDS))
                    .isEqualTo("done");
        }
    }
}
