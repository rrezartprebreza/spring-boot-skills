package com.example.verification;

import com.example.config.ExternalIdentity;
import com.example.config.JwtClaimExtractor;
import com.example.config.ResourceServerConfig;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.Filter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExternalIdentityTest {
    private static final String ISSUER = "https://issuer.example";
    private static final SecretKey KEY = new SecretKeySpec(
        "test-only-signing-key-32-bytes-long".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({ResourceServerConfig.class, JwtClaimExtractor.class})
    static class App {
        @Bean JwtDecoder decoder() {
            var decoder = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
            OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains("orders")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER), audience));
            return decoder;
        }
        @Bean Profiles profiles(JwtClaimExtractor claims) { return new Profiles(claims); }
    }

    @RestController
    static class Profiles {
        private final JwtClaimExtractor claims;
        private final Map<ExternalIdentity, String> accounts =
            Map.of(new ExternalIdentity(ISSUER, "user-a"), "account-a");
        Profiles(JwtClaimExtractor claims) { this.claims = claims; }
        @GetMapping("/profile") String profile() {
            return accounts.getOrDefault(claims.getIdentity(), "unlinked");
        }
        @GetMapping("/admin") @PreAuthorize("hasAuthority('SCOPE_admin')")
        String admin() { return "admin"; }
    }

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(App.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }

    private String token(String issuer, String subject, String email, String audience, Instant expires) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(subject)
            .audience(List.of(audience)).issuedAt(Instant.now().minusSeconds(600)).expiresAt(expires);
        if (email != null) claims.claim("email", email);
        return new NimbusJwtEncoder(new ImmutableSecret<>(KEY)).encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }
    private String valid(String subject, String email) {
        return token(ISSUER, subject, email, "orders", Instant.now().plusSeconds(300));
    }

    @Test void absentOrChangedEmailDoesNotChangeAccount() throws Exception {
        for (String email : new String[] {null, "old@example.com", "new@example.com"}) {
            mvc.perform(get("/profile").header("Authorization", "Bearer " + valid("user-a", email)))
                .andExpect(status().isOk()).andExpect(content().string("account-a"));
        }
    }
    @Test void reusedEmailDoesNotLinkDifferentSubject() throws Exception {
        mvc.perform(get("/profile").header("Authorization", "Bearer " + valid("user-b", "old@example.com")))
            .andExpect(status().isOk()).andExpect(content().string("unlinked"));
    }
    @Test void issuerAndSubjectAreBothPartOfIdentity() {
        assertNotEquals(new ExternalIdentity(ISSUER, "user-a"),
            new ExternalIdentity("https://other.example", "user-a"));
        assertNotEquals(new ExternalIdentity(ISSUER, "User-a"), new ExternalIdentity(ISSUER, "user-a"));
    }
    @Test void rejectsWrongIssuerAudienceExpiryAndSignature() throws Exception {
        List<String> invalid = List.of(
            token("https://untrusted.example", "user-a", null, "orders", Instant.now().plusSeconds(300)),
            token(ISSUER, "user-a", null, "another-api", Instant.now().plusSeconds(300)),
            token(ISSUER, "user-a", null, "orders", Instant.now().minusSeconds(300)),
            "invalid.token.signature");
        for (String value : invalid) {
            mvc.perform(get("/profile").header("Authorization", "Bearer " + value))
                .andExpect(status().isUnauthorized());
        }
    }
    @Test void authenticatedUserWithoutScopeIsForbidden() throws Exception {
        mvc.perform(get("/admin").header("Authorization", "Bearer " + valid("user-a", null)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/profile")).andExpect(status().isUnauthorized());
    }
    @Test void missingIdentityOrAuthenticationFailsClosed() {
        assertThrows(BadCredentialsException.class, () -> new ExternalIdentity(ISSUER, " "));
        assertThrows(BadCredentialsException.class, () -> new ExternalIdentity(null, "user-a"));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> new JwtClaimExtractor().getIdentity());
    }
}
