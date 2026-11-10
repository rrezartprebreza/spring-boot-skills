package com.example.config;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Extracts claims from the current JWT token.
 * Inject this into services that need to know the authenticated user.
 */
@Component
public class JwtClaimExtractor {

    public ExternalIdentity getIdentity() {
        return ExternalIdentity.from(getJwt());
    }

    // Opaque provider id ("auth0|...", Okta "00u..."): map it to your user record, don't parse it.
    public String getSubject() {
        return getClaim("sub");
    }

    public String getEmail() {
        return getClaim("email");
    }

    public String getUsername() {
        return getClaim("preferred_username");
    }

    /**
     * Extract roles from standard OAuth2 scope claim.
     */
    public List<String> getScopes() {
        String scope = getClaim("scope");
        return scope != null ? List.of(scope.split(" ")) : List.of();
    }

    /**
     * Extract roles from Keycloak's nested realm_access.roles claim.
     * Only needed when using Keycloak as the identity provider.
     */
    @SuppressWarnings("unchecked")
    public List<String> getKeycloakRoles() {
        Jwt jwt = getJwt();
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) return Collections.emptyList();
        Object roles = realmAccess.get("roles");
        return roles instanceof List ? (List<String>) roles : Collections.emptyList();
    }

    private String getClaim(String claimName) {
        return getJwt().getClaimAsString(claimName);
    }

    private Jwt getJwt() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated JWT required");
        }
        return jwt;
    }
}
