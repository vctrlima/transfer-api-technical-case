package com.personal.transfer.interfaces.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BalanceResponse(
        String accountId,
        String customerName,
        BigDecimal balance,
        BigDecimal availableLimit,
        BigDecimal dailyLimitUsed,
        BigDecimal dailyLimitRemaining,
        LocalDateTime updatedAt
) {
}
