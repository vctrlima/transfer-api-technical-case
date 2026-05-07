package com.personal.transfer.infrastructure.persistence;

import com.personal.transfer.domain.entities.Transfer;
import com.personal.transfer.domain.entities.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, String> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKeyAndStatusNot(String idempotencyKey, TransferStatus status);
}
