package com.example.auth.filter;

import com.example.auth.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final AccountStatusUserDetailsChecker accountChecker = new AccountStatusUserDetailsChecker();

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        boolean bearer = header != null && (header.equalsIgnoreCase("Bearer")
            || header.regionMatches(true, 0, "Bearer ", 0, 7));
        if (!bearer) {
            filterChain.doFilter(request, response);
            return;
        }

        final String username;
        try {
            String token = header.length() > 6 ? header.substring(7).trim() : "";
            username = jwtService.validateAccessTokenAndGetSubject(token);
        } catch (JwtException | IllegalArgumentException ex) {
            reject(response);
            return;
        }

        final UserDetails user;
        try {
            user = userDetailsService.loadUserByUsername(username);
            accountChecker.check(user);
        } catch (UsernameNotFoundException | AccountStatusException ex) {
            reject(response);
            return;
        }
        if (!username.equals(user.getUsername())) {
            reject(response);
            return;
        }

        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            user, null, user.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // Infrastructure and downstream failures must not be converted to credential errors.
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        response.setContentType("application/problem+json");
        response.getWriter().write(
            "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401}");
    }
}
