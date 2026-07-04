package com.example.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success,
    T data,
    ApiError error,
    Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null,
            new ApiError(code, message, List.of()), Instant.now());
    }

    public static <T> ApiResponse<T> validationError(List<String> violations) {
        return new ApiResponse<>(false, null,
            new ApiError("VALIDATION_FAILED", "Input validation failed", violations), Instant.now());
    }

    public record ApiError(String code, String message, List<String> details) {}
}
