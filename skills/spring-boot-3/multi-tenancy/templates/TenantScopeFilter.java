package com.example.tenancy;

import java.io.IOException;
import java.util.function.Predicate;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

public final class TenantScopeFilter extends OncePerRequestFilter {
    private final RequestMatcher routes;
    private final Predicate<String> activeTenant;

    public TenantScopeFilter(RequestMatcher routes, Predicate<String> activeTenant) {
        this.routes = routes;
        this.activeTenant = activeTenant;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !routes.matches(request);
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        TenantId tenant = TenantAccess.resolve(
            SecurityContextHolder.getContext().getAuthentication(), activeTenant);
        try (var ignored = TenantContext.open(tenant)) {
            chain.doFilter(request, response);
        }
    }
}
