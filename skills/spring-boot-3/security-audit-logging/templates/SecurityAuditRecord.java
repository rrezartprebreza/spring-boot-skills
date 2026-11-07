package com.example.securityaudit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record SecurityAuditRecord(
        UUID eventId, Instant occurredAt, UUID actorId, UUID tenantId,
        Action action, UUID targetId, Outcome outcome, String correlationId) {
    public enum Action { AUTHENTICATION, DOCUMENT_READ, ROLE_CHANGE, DATA_EXPORT }
    public enum Outcome { ATTEMPTED, DENIED, SUCCESS, FAILURE }

    private static final Pattern CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    public SecurityAuditRecord {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(action);
        Objects.requireNonNull(outcome);
        if (correlationId == null || !CORRELATION_ID.matcher(correlationId).matches()) {
            throw new IllegalArgumentException("Invalid correlation ID");
        }
        // Nullable identities support unauthenticated attempts, not unverified caller claims.
        if (outcome == Outcome.SUCCESS && (actorId == null || tenantId == null)) {
            throw new IllegalArgumentException("Successful events require verified actor and tenant");
        }
    }
}
