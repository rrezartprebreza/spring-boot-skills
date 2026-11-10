package com.example.ai.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiAuditAdvisorTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    static class App {
        @Bean AiAuditLogRepository repository() { return mock(AiAuditLogRepository.class); }
        @Bean AiAuditRecorder recorder(AiAuditLogRepository repository) { return new AiAuditRecorder(repository); }
        @Bean ThreadPoolTaskExecutor taskExecutor() {
            var executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(0);
            return executor;
        }
    }

    private AnnotationConfigApplicationContext context;
    private SimpleMeterRegistry registry;
    private AiAuditAdvisor advisor;
    private AiAuditLogRepository repository;
    private final CountDownLatch releaseWorker = new CountDownLatch(1);
    private final ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt("hello")).build();
    private final CallAdvisorChain chain = mock(CallAdvisorChain.class);

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(App.class);
        repository = context.getBean(AiAuditLogRepository.class);
        registry = new SimpleMeterRegistry();
        advisor = new AiAuditAdvisor(new AiMetrics(registry), context.getBean(AiAuditRecorder.class));
    }

    @AfterEach void cleanup() {
        releaseWorker.countDown();
        context.close();
        registry.close();
    }

    private void saturateExecutor() throws InterruptedException {
        var started = new CountDownLatch(1);
        context.getBean(ThreadPoolTaskExecutor.class).execute(() -> {
            started.countDown();
            try {
                releaseWorker.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
    }

    @Test void saturatedExecutorDoesNotLoseSuccessfulResponse() throws Exception {
        saturateExecutor();
        var response = ChatClientResponse.builder().build();
        when(chain.nextCall(request)).thenReturn(response);
        assertSame(response, advisor.adviseCall(request, chain));
        verifyNoInteractions(repository);
    }

    @Test void saturatedExecutorPreservesOriginalModelException() throws Exception {
        saturateExecutor();
        var original = new IllegalStateException("provider failure");
        when(chain.nextCall(request)).thenThrow(original);
        assertSame(original, assertThrows(IllegalStateException.class, () -> advisor.adviseCall(request, chain)));
        verifyNoInteractions(repository);
    }

    @Test void stoppedExecutorDoesNotLoseSuccessfulResponse() {
        context.getBean(ThreadPoolTaskExecutor.class).shutdown();
        var response = ChatClientResponse.builder().build();
        when(chain.nextCall(request)).thenReturn(response);
        assertSame(response, advisor.adviseCall(request, chain));
    }

    @Test void acceptedAuditRecordsSuccess() {
        var response = ChatClientResponse.builder().build();
        when(chain.nextCall(request)).thenReturn(response);
        assertSame(response, advisor.adviseCall(request, chain));
        verify(repository, timeout(5000)).save(argThat(entry ->
            entry.status().equals("SUCCESS") && entry.errorMessage() == null));
    }

    @Test void modelFailureRecordsOnlyErrorType() {
        var original = new IllegalArgumentException("private prompt");
        when(chain.nextCall(request)).thenThrow(original);
        assertSame(original, assertThrows(IllegalArgumentException.class, () -> advisor.adviseCall(request, chain)));
        verify(repository, timeout(5000)).save(argThat(entry ->
            entry.status().equals("ERROR") && entry.errorMessage().equals("IllegalArgumentException")));
    }

    @Test void persistenceFailureIsContainedInsideRecorder() {
        doThrow(new IllegalStateException("database unavailable")).when(repository).save(any());
        var recorder = new AiAuditRecorder(repository);
        assertDoesNotThrow(() -> recorder.record(UUID.randomUUID().toString(), "SUCCESS", null, Instant.now()));
        verify(repository).save(any());
    }

    @Test void modelFailureDoesNotLogProviderMessageOrStackTrace() {
        var logger = (Logger) LoggerFactory.getLogger(AiAuditAdvisor.class);
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            when(chain.nextCall(request)).thenThrow(new IllegalStateException("private prompt"));
            assertThrows(IllegalStateException.class, () -> advisor.adviseCall(request, chain));
            assertFalse(events.list.isEmpty());
            assertTrue(events.list.stream().noneMatch(event ->
                event.getFormattedMessage().contains("private prompt") || event.getThrowableProxy() != null));
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }
}
