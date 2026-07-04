package com.example.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for all domain-specific errors.
 * Subclasses define their own error code and HTTP status.
 */
public abstract class DomainException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    protected DomainException(String errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getHttpStatus() { return httpStatus; }
}

// --- Concrete exceptions ---

package com.example.order.exception;

import com.example.common.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class OrderNotFoundException extends DomainException {
    public OrderNotFoundException(UUID orderId) {
        super("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND,
            "Order not found: " + orderId);
    }
}

public class InsufficientInventoryException extends DomainException {
    public InsufficientInventoryException(UUID productId, int requested, int available) {
        super("INSUFFICIENT_INVENTORY", HttpStatus.UNPROCESSABLE_ENTITY,
            "Insufficient inventory for product %s: requested %d, available %d"
                .formatted(productId, requested, available));
    }
}

public class BusinessRuleViolationException extends DomainException {
    public BusinessRuleViolationException(String message) {
        super("BUSINESS_RULE_VIOLATION", HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
