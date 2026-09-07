package com.example.verification;

import com.example.auth.filter.JwtAuthenticationFilter;
import com.example.auth.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class JwtFilterTest {
    private static final byte[] KEY = "01234567890123456789012345678901".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private JwtService service;
    private UserDetails user;

    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", Base64.getEncoder().encodeToString(KEY));
        ReflectionTestUtils.setField(service, "accessTokenExpiration", 900000L);
        ReflectionTestUtils.setField(service, "refreshTokenExpiration", 604800000L);
        user = User.withUsername("alice").password("unused").roles("USER").build();
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    private MockHttpServletResponse execute(String token, UserDetails account, boolean expectChain) throws Exception {
        var filter = new JwtAuthenticationFilter(service, username -> {
            if (account == null) throw new UsernameNotFoundException("deleted");
            return account;
        });
        var request = new MockHttpServletRequest();
        if (token != null) request.addHeader("Authorization", "Bearer " + token);
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertEquals(expectChain, called.get());
        return response;
    }

    @Test void authenticatesValidAccessToken() throws Exception {
        execute(service.generateAccessToken(user), user, true);
        assertEquals("alice", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test void missingHeaderContinuesWithoutAuthentication() throws Exception {
        execute(null, user, true);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @ParameterizedTest @ValueSource(strings = {"", "invalid", "refresh", "expired", "tampered", "no-expiration", "no-subject"})
    void rejectsInvalidTokens(String kind) throws Exception {
        String token = switch (kind) {
            case "refresh" -> service.generateRefreshToken(user);
            case "expired" -> Jwts.builder().subject("alice").claim("type", "access")
                .expiration(Date.from(Instant.now().minusSeconds(60))).signWith(Keys.hmacShaKeyFor(KEY)).compact();
            case "tampered" -> Jwts.builder().subject("alice").claim("type", "access")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor("different-key-01234567890123456789".getBytes())).compact();
            case "no-expiration" -> Jwts.builder().subject("alice").claim("type", "access")
                .signWith(Keys.hmacShaKeyFor(KEY)).compact();
            case "no-subject" -> Jwts.builder().claim("type", "access")
                .expiration(Date.from(Instant.now().plusSeconds(60))).signWith(Keys.hmacShaKeyFor(KEY)).compact();
            default -> kind;
        };
        var response = execute(token, user, false);
        assertEquals(401, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        assertTrue(response.getHeader("WWW-Authenticate").contains("invalid_token"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @ParameterizedTest @ValueSource(strings = {"disabled", "locked", "expired", "credentials", "deleted"})
    void rejectsInactiveAccounts(String state) throws Exception {
        var builder = User.withUsername("alice").password("unused").roles("USER");
        switch (state) {
            case "disabled" -> builder.disabled(true);
            case "locked" -> builder.accountLocked(true);
            case "expired" -> builder.accountExpired(true);
            case "credentials" -> builder.credentialsExpired(true);
        }
        var response = execute(service.generateAccessToken(user), state.equals("deleted") ? null : builder.build(), false);
        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test void doesNotMaskDatabaseOutage() {
        var filter = new JwtAuthenticationFilter(service, username -> { throw new IllegalStateException("database unavailable"); });
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + service.generateAccessToken(user));
        assertThrows(IllegalStateException.class, () -> filter.doFilter(request, new MockHttpServletResponse(), (a,b) -> {}));
    }

    @Test void doesNotMaskDownstreamFailure() {
        var filter = new JwtAuthenticationFilter(service, username -> user);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + service.generateAccessToken(user));
        assertThrows(IOException.class, () -> filter.doFilter(request, new MockHttpServletResponse(),
            (a,b) -> { throw new IOException("downstream"); }));
    }

    @Test void doesNotMaskAuthenticationServiceFailure() {
        var filter = new JwtAuthenticationFilter(service, username -> {
            throw new org.springframework.security.authentication.AuthenticationServiceException("identity store unavailable");
        });
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + service.generateAccessToken(user));
        assertThrows(org.springframework.security.authentication.AuthenticationServiceException.class,
            () -> filter.doFilter(request, new MockHttpServletResponse(), (a,b) -> {}));
    }

    @Test void rejectsBareBearerHeader() throws Exception {
        var filter = new JwtAuthenticationFilter(service, username -> user);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (a,b) -> fail("Invalid token reached endpoint"));
        assertEquals(401, response.getStatus());
    }
}
