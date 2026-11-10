package com.example.tenancy;

import java.util.function.Predicate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class TenantAccess {
    private TenantAccess() {}

    public static TenantId resolve(Authentication authentication, Predicate<String> activeTenant) {
        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
            throw new InsufficientAuthenticationException("Authenticated JWT required");
        }
        // Only a claim from the resource server's verified, issuer/audience-checked token.
        Object claim = token.getToken().getClaims().get("tenant_id");
        if (!(claim instanceof String value) || value.isBlank() || value.length() > 128
                || !activeTenant.test(value)) {
            throw new AccessDeniedException("Tenant unavailable");
        }
        return new TenantId(value);
    }
}
