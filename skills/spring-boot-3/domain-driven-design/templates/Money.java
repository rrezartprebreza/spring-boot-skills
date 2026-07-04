package com.example.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Value object representing monetary amount with currency.
 * Immutable — all operations return new instances.
 */
public record Money(BigDecimal amount, Currency currency) {

    public static final Money ZERO = Money.usd("0.00");

    public Money {
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Amount cannot be negative: " + amount);
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money usd(String amount) {
        return of(amount, "USD");
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        BigDecimal result = amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Result would be negative");
        return new Money(result, currency);
    }

    public Money multiply(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    private void assertSameCurrency(Money other) {
        if (!currency.equals(other.currency))
            throw new IllegalArgumentException(
                "Currency mismatch: %s vs %s".formatted(currency, other.currency));
    }

    @Override
    public String toString() {
        return "%s %s".formatted(currency.getCurrencyCode(), amount);
    }
}
