package com.example.order.exception;

import com.example.common.exception.DomainException;
import org.springframework.http.HttpStatus;
import java.util.UUID;

public class InsufficientInventoryException extends DomainException {
    public InsufficientInventoryException(UUID productId, int requested, int available) {
        super("INSUFFICIENT_INVENTORY", HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient inventory for product %s: requested %d, available %d".formatted(productId, requested, available));
    }
}
