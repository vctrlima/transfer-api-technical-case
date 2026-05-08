package com.personal.transfer.application.saga;

import com.personal.transfer.application.dto.CustomerInfo;
import com.personal.transfer.domain.entities.Transfer;
import com.personal.transfer.domain.entities.TransferStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SagaContext {

    private String transferId;
    private String originAccountId;
    private String destinationAccountId;
    private BigDecimal amount;
    private String idempotencyKey;
    private String description;

    private CustomerInfo customer;
    private boolean transferExecuted;
    private Transfer pendingTransfer;

    @Builder.Default
    private TransferStatus sagaStatus = TransferStatus.PROCESSING;
}
