package com.personal.transfer.application.saga.steps;

import com.personal.transfer.application.saga.SagaContext;
import com.personal.transfer.application.saga.SagaStep;
import com.personal.transfer.domain.entities.Account;
import com.personal.transfer.infrastructure.persistence.AccountRepository;
import com.personal.transfer.infrastructure.redis.BalanceCacheRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecuteTransferStep implements SagaStep<SagaContext> {

    private final AccountRepository accountRepository;
    private final BalanceCacheRepository balanceCacheRepository;

    @Override
    @Transactional
    public void execute(SagaContext context) {
        log.info("[SAGA][Step4:ExecuteTransfer] Iniciando débito/crédito para transferId={}", context.getTransferId());

        Account origin = accountRepository
                .findByIdWithLock(context.getOriginAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Origin account not found: " + context.getOriginAccountId()));

        Account destination = accountRepository
                .findByIdWithLock(context.getDestinationAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Destination account not found: " + context.getDestinationAccountId()));

        origin.debit(context.getAmount());
        destination.credit(context.getAmount());

        accountRepository.save(origin);
        accountRepository.save(destination);

        balanceCacheRepository.evict(context.getOriginAccountId());
        balanceCacheRepository.evict(context.getDestinationAccountId());

        context.setTransferExecuted(true);
        log.info("[SAGA][Step4:ExecuteTransfer] Débito/crédito concluído para transferId={}", context.getTransferId());
    }

    @Override
    @Transactional
    public void compensate(SagaContext context) {
        if (!context.isTransferExecuted()) {
            log.info("[SAGA][Step4:ExecuteTransfer][Compensate] Nenhuma ação necessária (débito não realizado) para transferId={}", context.getTransferId());
            return;
        }

        log.info("[SAGA][Step4:ExecuteTransfer][Compensate] Revertendo débito/crédito para transferId={}", context.getTransferId());

        Account origin = accountRepository
                .findByIdWithLock(context.getOriginAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Origin account not found during compensation: " + context.getOriginAccountId()));

        Account destination = accountRepository
                .findByIdWithLock(context.getDestinationAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Destination account not found during compensation: " + context.getDestinationAccountId()));

        origin.credit(context.getAmount());
        destination.debit(context.getAmount());

        accountRepository.save(origin);
        accountRepository.save(destination);

        balanceCacheRepository.evict(context.getOriginAccountId());
        balanceCacheRepository.evict(context.getDestinationAccountId());

        log.info("[SAGA][Step4:ExecuteTransfer][Compensate] Rollback concluído para transferId={}", context.getTransferId());
    }
}
