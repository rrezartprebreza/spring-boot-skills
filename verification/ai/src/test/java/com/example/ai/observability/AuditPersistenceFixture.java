package com.example.ai.observability;

import java.time.Instant;
import java.util.UUID;

// Application-owned persistence contract required by the shipped advisor.
// This fixture does not claim to verify JPA mappings or database delivery.
record AiAuditLog(UUID id, String status, String errorMessage, Instant startedAt, Instant completedAt) {}

interface AiAuditLogRepository {
    void save(AiAuditLog entry);
}
