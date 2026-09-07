package com.example.order.exception;

import com.example.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class BusinessRuleViolationException extends DomainException {
    public BusinessRuleViolationException(String message) {
        super("BUSINESS_RULE_VIOLATION", HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
