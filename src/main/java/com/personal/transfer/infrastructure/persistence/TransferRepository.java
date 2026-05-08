package com.personal.transfer.infrastructure.persistence;

import com.personal.transfer.application.ports.out.TransferPort;
import com.personal.transfer.domain.entities.Transfer;
import com.personal.transfer.domain.entities.TransferStatus;
import com.personal.transfer.infrastructure.persistence.entities.TransferJpaEntity;
import com.personal.transfer.infrastructure.persistence.mappers.TransferPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TransferRepository implements TransferPort {

    private final JpaTransferRepository jpaRepository;

    @Override
    public Optional<Transfer> findById(String transferId) {
        return jpaRepository.findById(transferId)
                .map(TransferPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Transfer> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey)
                .map(TransferPersistenceMapper::toDomain);
    }

    @Override
    public boolean existsByIdempotencyKeyAndStatusNot(String idempotencyKey, TransferStatus status) {
        return jpaRepository.existsByIdempotencyKeyAndStatusNot(idempotencyKey, status);
    }

    @Override
    public Transfer save(Transfer transfer) {
        return TransferPersistenceMapper.toDomain(
                jpaRepository.save(TransferPersistenceMapper.toEntity(transfer))
        );
    }
}

interface JpaTransferRepository extends JpaRepository<TransferJpaEntity, String> {

    Optional<TransferJpaEntity> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKeyAndStatusNot(String idempotencyKey, TransferStatus status);
}
