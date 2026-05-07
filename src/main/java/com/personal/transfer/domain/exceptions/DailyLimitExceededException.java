package com.personal.transfer.domain.exceptions;

import java.math.BigDecimal;

public class DailyLimitExceededException extends RuntimeException {

    public DailyLimitExceededException(BigDecimal accumulated, BigDecimal amount, BigDecimal limit) {
        super("Daily limit exceeded. Accumulated: " + accumulated + ", Requested: " + amount + ", Limit: " + limit);
    }
}
