package com.personal.transfer.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BalanceResult(
        String accountId,
        String customerName,
        BigDecimal balance,
        BigDecimal availableLimit,
        BigDecimal dailyLimitUsed,
        BigDecimal dailyLimitRemaining,
        LocalDateTime updatedAt
) {
}
