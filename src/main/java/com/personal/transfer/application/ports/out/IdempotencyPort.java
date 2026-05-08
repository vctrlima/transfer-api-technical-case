package com.personal.transfer.application.ports.out;

import com.personal.transfer.application.dto.TransferResult;

import java.util.Optional;

public interface IdempotencyPort {

    Optional<TransferResult> findTransferResult(String idempotencyKey);

    void saveTransferResult(String idempotencyKey, TransferResult response);
}
