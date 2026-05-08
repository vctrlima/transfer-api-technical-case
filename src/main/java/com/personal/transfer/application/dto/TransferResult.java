package com.personal.transfer.application.dto;

import com.personal.transfer.domain.entities.Transfer;
import com.personal.transfer.domain.entities.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResult(
        String transferId,
        TransferStatus status,
        BigDecimal amount,
        String originAccountId,
        String destinationAccountId,
        LocalDateTime createdAt
) {
    public static TransferResult from(Transfer transfer) {
        return new TransferResult(
                transfer.getId(),
                transfer.getStatus(),
                transfer.getAmount(),
                transfer.getOriginAccountId(),
                transfer.getDestinationAccountId(),
                transfer.getCreatedAt()
        );
    }
}
