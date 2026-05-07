package com.personal.transfer.infrastructure.adapters.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BacenNotifyRequest(
        String transferId,
        String originAccountId,
        String destinationAccountId,
        BigDecimal amount,
        LocalDateTime occurredAt
) {
}
