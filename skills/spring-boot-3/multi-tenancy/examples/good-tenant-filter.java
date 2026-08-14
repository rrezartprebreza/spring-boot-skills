import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class TenantContextFilter extends OncePerRequestFilter {

    private final TenantResolver tenantResolver;

    TenantContextFilter(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String tenantId = tenantResolver.fromVerifiedAuthentication(authentication);

        try {
            TenantContext.set(tenantId);
            filterChain.doFilter(request, response);
        }
        finally {
            TenantContext.clear();
        }
    }
}
