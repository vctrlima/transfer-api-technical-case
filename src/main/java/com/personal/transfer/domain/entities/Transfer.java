package com.personal.transfer.domain.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transfer {

    private String id;
    private String originAccountId;
    private String destinationAccountId;
    private BigDecimal amount;
    private TransferStatus status;
    private String idempotencyKey;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static Transfer start(String id,
                                 String originAccountId,
                                 String destinationAccountId,
                                 BigDecimal amount,
                                 String idempotencyKey,
                                 String description) {
        LocalDateTime now = LocalDateTime.now();
        return Transfer.builder()
                .id(id)
                .originAccountId(originAccountId)
                .destinationAccountId(destinationAccountId)
                .amount(amount)
                .status(TransferStatus.PROCESSING)
                .idempotencyKey(idempotencyKey)
                .description(description)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void markAs(TransferStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
}
