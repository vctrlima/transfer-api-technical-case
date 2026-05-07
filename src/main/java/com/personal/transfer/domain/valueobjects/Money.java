package com.personal.transfer.domain.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal amount) {

    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be a positive value");
        }
        try {
            amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount must have at most 2 decimal places");
        }
    }

    public BigDecimal value() {
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
