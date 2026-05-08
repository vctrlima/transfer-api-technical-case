package com.personal.transfer.infrastructure.persistence.mappers;

import com.personal.transfer.domain.entities.Transfer;
import com.personal.transfer.infrastructure.persistence.entities.TransferJpaEntity;

public final class TransferPersistenceMapper {

    private TransferPersistenceMapper() {
    }

    public static Transfer toDomain(TransferJpaEntity entity) {
        return Transfer.builder()
                .id(entity.getId())
                .originAccountId(entity.getOriginAccountId())
                .destinationAccountId(entity.getDestinationAccountId())
                .amount(entity.getAmount())
                .status(entity.getStatus())
                .idempotencyKey(entity.getIdempotencyKey())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static TransferJpaEntity toEntity(Transfer transfer) {
        return TransferJpaEntity.builder()
                .id(transfer.getId())
                .originAccountId(transfer.getOriginAccountId())
                .destinationAccountId(transfer.getDestinationAccountId())
                .amount(transfer.getAmount())
                .status(transfer.getStatus())
                .idempotencyKey(transfer.getIdempotencyKey())
                .description(transfer.getDescription())
                .createdAt(transfer.getCreatedAt())
                .updatedAt(transfer.getUpdatedAt())
                .build();
    }
}
