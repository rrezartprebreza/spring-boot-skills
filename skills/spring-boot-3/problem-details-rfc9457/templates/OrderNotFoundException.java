package com.example.order.exception;

import com.example.common.exception.DomainException;
import org.springframework.http.HttpStatus;
import java.util.UUID;

public class OrderNotFoundException extends DomainException {
    public OrderNotFoundException(UUID orderId) {
        super("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND, "Order not found: " + orderId);
    }
}
