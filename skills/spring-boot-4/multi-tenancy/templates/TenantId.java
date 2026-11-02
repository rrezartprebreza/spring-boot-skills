package com.example.tenancy;

public record TenantId(String value) {
    public TenantId {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("Tenant ID must be nonblank and at most 128 characters");
        }
    }
}
