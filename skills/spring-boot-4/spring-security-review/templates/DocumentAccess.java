package com.example.securityreview;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

public final class DocumentAccess {
    public record AccountIdentity(UUID tenantId, UUID accountId) {
        public AccountIdentity {
            Objects.requireNonNull(tenantId);
            Objects.requireNonNull(accountId);
        }
    }

    public interface Store {
        Optional<String> findByIdAndTenantIdAndOwnerId(UUID documentId, UUID tenantId, UUID ownerId);
    }

    private final Store store;

    public DocumentAccess(Store store) {
        this.store = Objects.requireNonNull(store);
    }

    // AccountIdentity must be created by trusted authentication/identity mapping, not request binding.
    public Optional<String> read(Authentication authentication, UUID documentId) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AccountIdentity identity)
                || authentication.getAuthorities().stream()
                    .noneMatch(authority -> "documents:read".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Document access denied");
        }
        Objects.requireNonNull(documentId);
        return store.findByIdAndTenantIdAndOwnerId(documentId, identity.tenantId(), identity.accountId());
    }
}
