package com.personal.transfer.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BacenTransferEvent(
        String transferId,
        String originAccountId,
        String destinationAccountId,
        BigDecimal amount,
        LocalDateTime occurredAt
) {
}
