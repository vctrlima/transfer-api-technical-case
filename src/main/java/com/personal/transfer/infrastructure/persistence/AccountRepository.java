package com.personal.transfer.infrastructure.persistence;

import com.personal.transfer.application.ports.out.AccountPort;
import com.personal.transfer.domain.entities.Account;
import com.personal.transfer.infrastructure.persistence.entities.AccountJpaEntity;
import com.personal.transfer.infrastructure.persistence.mappers.AccountPersistenceMapper;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AccountRepository implements AccountPort {

    private final JpaAccountRepository jpaRepository;

    @Override
    public Optional<Account> findById(String accountId) {
        return jpaRepository.findById(accountId)
                .map(AccountPersistenceMapper::toDomain);
    }

    @Override
    public List<Account> findAllByIds(List<String> accountIds) {
        return jpaRepository.findAllByIds(accountIds).stream()
                .map(AccountPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<Account> findAllByIdsWithLock(List<String> accountIds) {
        return jpaRepository.findAllByIdsWithLock(accountIds).stream()
                .map(AccountPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<Account> saveAll(List<Account> accounts) {
        return jpaRepository.saveAll(accounts.stream()
                        .map(AccountPersistenceMapper::toEntity)
                        .toList())
                .stream()
                .map(AccountPersistenceMapper::toDomain)
                .toList();
    }
}

interface JpaAccountRepository extends JpaRepository<AccountJpaEntity, String> {

    /**
     * Fetches both accounts in a single query without locking.
     * Used by ValidateAccountStep for a cheap fail-fast pre-check before
     * acquiring locks in the ExecuteTransferStep.
     * ORDER BY id prevents deadlocks under concurrent requests.
     */
    @Query("SELECT a FROM AccountJpaEntity a WHERE a.id IN :ids ORDER BY a.id")
    List<AccountJpaEntity> findAllByIds(@Param("ids") List<String> ids);

    /**
     * Fetches and locks both accounts atomically in a single round-trip.
     * ORDER BY id is critical — it enforces consistent lock ordering across
     * concurrent transactions and prevents deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountJpaEntity a WHERE a.id IN :ids ORDER BY a.id")
    List<AccountJpaEntity> findAllByIdsWithLock(@Param("ids") List<String> ids);
}
