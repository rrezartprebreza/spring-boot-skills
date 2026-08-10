try {
    TenantContext.set(tenantResolver.from(authentication));
    filterChain.doFilter(request, response);
} finally {
    TenantContext.clear();
}
