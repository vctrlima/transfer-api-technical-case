package com.personal.transfer.interfaces.dto;

import com.personal.transfer.domain.entities.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResponse(
        String transferId,
        TransferStatus status,
        BigDecimal amount,
        String originAccountId,
        String destinationAccountId,
        LocalDateTime createdAt
) {
}
