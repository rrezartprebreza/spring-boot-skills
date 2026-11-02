package com.example.config;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

/** Account key from an already verified access token, not a token validator. */
public record ExternalIdentity(String issuer, String subject) {
    public ExternalIdentity {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new BadCredentialsException("Issuer and subject are required");
        }
    }

    public static ExternalIdentity from(Jwt jwt) {
        // Preserve exact issuer and opaque subject; do not lowercase or trim either.
        return new ExternalIdentity(jwt.getClaimAsString("iss"), jwt.getSubject());
    }
}
