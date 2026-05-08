package com.personal.transfer.application.ports.out;

import java.math.BigDecimal;

public interface DailyLimitPort {

    BigDecimal incrementAndGet(String accountId, BigDecimal amount);

    void decrement(String accountId, BigDecimal amount);

    BigDecimal getAccumulated(String accountId);
}
