package com.personal.transfer.infrastructure.persistence;

import com.personal.transfer.domain.entities.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") String id);

    /**
     * Fetches both accounts in a single query without locking.
     * Used by ValidateAccountStep for a cheap fail-fast pre-check before
     * acquiring locks in the ExecuteTransferStep.
     * ORDER BY id prevents deadlocks under concurrent requests.
     */
    @Query("SELECT a FROM Account a WHERE a.id IN :ids ORDER BY a.id")
    List<Account> findAllByIds(@Param("ids") List<String> ids);

    /**
     * Fetches and locks both accounts atomically in a single round-trip.
     * ORDER BY id is critical — it enforces consistent lock ordering across
     * concurrent transactions and prevents deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id IN :ids ORDER BY a.id")
    List<Account> findAllByIdsWithLock(@Param("ids") List<String> ids);
}
