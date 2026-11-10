package com.example.tasks;

import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StaleOrderSchedule {
    private final Clock clock;
    private final SweepAction action;

    public StaleOrderSchedule(Clock clock, SweepAction action) {
        this.clock = clock;
        this.action = action;
    }

    @Scheduled(cron = "${jobs.stale-orders.cron:-}", zone = "${jobs.stale-orders.zone:UTC}")
    public void run() {
        action.processDue(clock.instant(), 100);
    }

    @FunctionalInterface
    public interface SweepAction {
        // Implement atomic claims, tenant isolation and idempotent effects in the application.
        void processDue(Instant cutoff, int batchSize);
    }
}
