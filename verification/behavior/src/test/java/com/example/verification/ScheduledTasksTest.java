package com.example.verification;

import com.example.tasks.StaleOrderSchedule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.support.CronExpression;
import static org.assertj.core.api.Assertions.*;

class ScheduledTasksTest {
    private final Instant now = Instant.parse("2026-11-10T12:04:00Z");
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Scheduling.class)
            .withBean(Clock.class, () -> Clock.fixed(now, ZoneOffset.UTC))
            .withBean(StaleOrderSchedule.SweepAction.class, () -> (cutoff, batch) -> {})
            .withBean(StaleOrderSchedule.class);

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class Scheduling {}

    @Test void disabledByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
        });
    }

    @Test void explicitCronRegistersExactlyOneTrigger() {
        runner.withPropertyValues("jobs.stale-orders.cron=0 0 0 1 1 *", "jobs.stale-orders.zone=UTC")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).hasSize(1);
                });
    }

    @Test void malformedCronFailsStartup() {
        runner.withPropertyValues("jobs.stale-orders.cron=*/5 * * * *")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void sixFieldCronUsesSecondsAndKnownZone() {
        var cron = CronExpression.parse("0 */5 * * * *");
        assertThat(cron.next(now.atZone(ZoneOffset.UTC)))
                .isEqualTo(ZonedDateTime.parse("2026-11-10T12:05:00Z"));
    }

    @Test void adapterPassesOneBoundedSweepAndDoesNotHideFailures() {
        AtomicInteger calls = new AtomicInteger();
        var task = new StaleOrderSchedule(Clock.fixed(now, ZoneOffset.UTC), (cutoff, batch) -> {
            assertThat(cutoff).isEqualTo(now);
            assertThat(batch).isEqualTo(100);
            calls.incrementAndGet();
            throw new IllegalStateException("claim failed");
        });
        assertThatThrownBy(task::run).isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(1);
    }
}
