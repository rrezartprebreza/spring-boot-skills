package com.example.aisecurity;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class DocumentReadTool {
    public record Caller(UUID tenantId, UUID accountId, Set<String> permissions) {
        public Caller {
            Objects.requireNonNull(tenantId);
            Objects.requireNonNull(accountId);
            permissions = Set.copyOf(permissions);
        }
    }

    public interface Reader {
        Optional<String> findAuthorized(UUID documentId, UUID tenantId, UUID accountId);
    }

    public static final class CallBudget {
        private final AtomicInteger remaining;

        public CallBudget(int maxCalls) {
            if (maxCalls < 1 || maxCalls > 16) {
                throw new IllegalArgumentException("Invalid tool call limit");
            }
            remaining = new AtomicInteger(maxCalls);
        }

        private void acquire() {
            if (remaining.getAndUpdate(value -> value > 0 ? value - 1 : 0) == 0) {
                throw new IllegalStateException("Tool call budget exhausted");
            }
        }
    }

    private final Caller caller;
    private final Reader reader;
    private final CallBudget budget;
    private final int maxResultChars;

    // Share the turn's budget across tool instances, retries and model iterations.
    public DocumentReadTool(Caller caller, Reader reader, CallBudget budget, int maxResultChars) {
        this.caller = Objects.requireNonNull(caller);
        this.reader = Objects.requireNonNull(reader);
        this.budget = Objects.requireNonNull(budget);
        if (maxResultChars < 1 || maxResultChars > 8000) {
            throw new IllegalArgumentException("Invalid tool limits");
        }
        this.maxResultChars = maxResultChars;
    }

    // This is the only model-facing argument. The adapter must not expose Caller or Reader.
    public String read(String documentId) {
        budget.acquire();
        if (!caller.permissions().contains("documents:read")) {
            throw new SecurityException("Document access denied");
        }
        if (documentId == null || documentId.length() != 36) {
            throw new IllegalArgumentException("Expected a canonical document UUID");
        }
        UUID id = UUID.fromString(documentId);
        if (!id.toString().equalsIgnoreCase(documentId)) {
            throw new IllegalArgumentException("Expected a canonical document UUID");
        }
        String body = reader.findAuthorized(id, caller.tenantId(), caller.accountId())
                .orElseThrow(() -> new SecurityException("Document unavailable"));
        if (body.length() > maxResultChars) {
            throw new IllegalStateException("Document exceeds tool result limit");
        }
        // Return as untrusted document content; the adapter must not promote it to instructions.
        return body;
    }
}
