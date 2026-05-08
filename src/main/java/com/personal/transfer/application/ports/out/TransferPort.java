package com.personal.transfer.application.ports.out;

import com.personal.transfer.domain.entities.Transfer;
import com.personal.transfer.domain.entities.TransferStatus;

import java.util.Optional;

public interface TransferPort {

    Optional<Transfer> findById(String transferId);

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKeyAndStatusNot(String idempotencyKey, TransferStatus status);

    Transfer save(Transfer transfer);
}
