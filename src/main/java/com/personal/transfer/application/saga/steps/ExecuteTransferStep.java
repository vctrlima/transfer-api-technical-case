package com.personal.transfer.application.saga.steps;

import com.personal.transfer.application.ports.out.AccountPort;
import com.personal.transfer.application.ports.out.BalanceCachePort;
import com.personal.transfer.application.ports.out.TransferPort;
import com.personal.transfer.application.saga.SagaContext;
import com.personal.transfer.application.saga.SagaStep;
import com.personal.transfer.domain.entities.Account;
import com.personal.transfer.domain.exceptions.AccountNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecuteTransferStep implements SagaStep<SagaContext> {

    private final AccountPort accountPort;
    private final BalanceCachePort balanceCachePort;
    private final TransferPort transferPort;

    /**
     * Executes debit/credit and persists the Transfer record in ONE transaction.
     *
     * <p>Previously the Transfer INSERT was a separate {@code transferRepository.save()}
     * call in the orchestrator, causing an extra DB transaction boundary (~12ms overhead).
     * By including {@code transferRepository.save(context.getPendingTransfer())} here,
     * the INSERT rides the same transaction as the account UPDATE batch.
     */
    @Override
    @Transactional
    public void execute(SagaContext context) {
        log.info("[SAGA][Step4:ExecuteTransfer] Iniciando débito/crédito para transferId={}", context.getTransferId());

        Map<String, Account> byId = fetchAccountsWithLock(
                context.getOriginAccountId(), context.getDestinationAccountId());

        Account origin = byId.get(context.getOriginAccountId());
        if (origin == null) {
            throw new AccountNotFoundException(context.getOriginAccountId());
        }
        Account destination = byId.get(context.getDestinationAccountId());
        if (destination == null) {
            throw new AccountNotFoundException(context.getDestinationAccountId());
        }

        origin.debit(context.getAmount());
        destination.credit(context.getAmount());

        accountPort.saveAll(List.of(origin, destination));

        if (context.getPendingTransfer() != null) {
            transferPort.save(context.getPendingTransfer());
        }

        balanceCachePort.evictAll(context.getOriginAccountId(), context.getDestinationAccountId());

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

        Map<String, Account> byId = fetchAccountsWithLock(
                context.getOriginAccountId(), context.getDestinationAccountId());

        Account origin = byId.get(context.getOriginAccountId());
        if (origin == null) {
            throw new AccountNotFoundException(context.getOriginAccountId());
        }
        Account destination = byId.get(context.getDestinationAccountId());
        if (destination == null) {
            throw new AccountNotFoundException(context.getDestinationAccountId());
        }

        origin.credit(context.getAmount());
        destination.debit(context.getAmount());

        accountPort.saveAll(List.of(origin, destination));
        balanceCachePort.evictAll(context.getOriginAccountId(), context.getDestinationAccountId());

        log.info("[SAGA][Step4:ExecuteTransfer][Compensate] Rollback concluído para transferId={}", context.getTransferId());
    }

    private Map<String, Account> fetchAccountsWithLock(String originId, String destinationId) {
        return accountPort.findAllByIdsWithLock(List.of(originId, destinationId))
                .stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
    }
}
