package com.personal.transfer.application.saga;

import com.personal.transfer.application.ports.out.TransferPort;
import com.personal.transfer.application.saga.steps.*;
import com.personal.transfer.domain.entities.Transfer;
import com.personal.transfer.domain.entities.TransferStatus;
import com.personal.transfer.domain.exceptions.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferSagaOrchestrator {

    private final FetchCustomerStep fetchCustomerStep;
    private final ValidateAccountStep validateAccountStep;
    private final ValidateLimitStep validateLimitStep;
    private final ExecuteTransferStep executeTransferStep;
    private final PublishBacenEventStep publishBacenEventStep;
    private final TransferPort transferPort;

    /**
     * Virtual-thread executor for parallel steps 2+3.
     * Virtual threads are cheap — no platform thread is blocked during I/O waits.
     */
    private static final Executor VIRTUAL_EXECUTOR =
            Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Executes the SAGA:
     * 1. ValidateAccount    — batch DB read, fail-fast pre-check (no lock, no Transfer record yet)
     * 2. ValidateLimit  ┐   — daily-limit port
     * 3. FetchCustomer  ┘   — customer gateway
     * 4. [Persist Transfer] — DB INSERT only after all validations pass; rejected requests produce ZERO DB writes
     * 5. ExecuteTransfer    — single batch SELECT FOR UPDATE + saveAll
     * 6. PublishBacenEvent  — sync SQS; on failure rollbacks step 5 + step 2
     */
    public Transfer execute(SagaContext context) {
        String transferId = UUID.randomUUID().toString();
        context.setTransferId(transferId);

        log.info("[SAGA] Iniciando para transferId={}, origin={}, destination={}, amount={}",
                transferId, context.getOriginAccountId(), context.getDestinationAccountId(), context.getAmount());

        try {
            validateAccountStep.execute(context);
        } catch (Exception e) {
            log.error("[SAGA][Step1:ValidateAccount] Falha de validação. transferId={}, erro={}", transferId, e.getMessage());
            throw e;
        }

        CompletableFuture<Void> limitFuture = CompletableFuture.runAsync(
                () -> validateLimitStep.execute(context), VIRTUAL_EXECUTOR);

        CompletableFuture<Void> customerFuture = CompletableFuture.runAsync(
                () -> fetchCustomerStep.execute(context), VIRTUAL_EXECUTOR);

        try {
            CompletableFuture.allOf(limitFuture, customerFuture).join();
        } catch (CompletionException ce) {
            boolean limitSucceeded;
            Throwable limitError = null;
            try {
                limitFuture.join();
                limitSucceeded = true;
            } catch (CompletionException | java.util.concurrent.CancellationException le) {
                limitSucceeded = false;
                limitError = (le instanceof CompletionException cle) ? cle.getCause() : le;
            }

            Throwable customerError = null;
            if (customerFuture.isCompletedExceptionally()) {
                try {
                    customerFuture.join();
                } catch (CompletionException cle) {
                    customerError = cle.getCause();
                }
            }

            if (limitSucceeded && customerError != null) {
                log.error("[SAGA][Step3:FetchCustomer] Falha — compensando limite diário. transferId={}", transferId);
                try {
                    validateLimitStep.compensate(context);
                } catch (Exception ce2) {
                    log.error("[SAGA][Compensate][Step2] Falha. transferId={}: {}", transferId, ce2.getMessage());
                }
                if (customerError instanceof ExternalServiceException ese) throw ese;
                throw new ExternalServiceException("Cadastro API error: " + customerError.getMessage(), customerError);
            }
            if (!limitSucceeded && limitError != null) {
                log.error("[SAGA][Step2:ValidateLimit] Falha de validação. transferId={}", transferId);
                customerFuture.cancel(true);
                if (limitError instanceof RuntimeException re) throw re;
                throw new ExternalServiceException("Limit validation error: " + limitError.getMessage(), limitError);
            }
            Throwable cause = ce.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new ExternalServiceException("Parallel step error: " + cause.getMessage(), cause);
        }

        Transfer transfer = Transfer.start(
                transferId,
                context.getOriginAccountId(),
                context.getDestinationAccountId(),
                context.getAmount(),
                context.getIdempotencyKey(),
                context.getDescription()
        );
        context.setPendingTransfer(transfer);

        try {
            executeTransferStep.execute(context);
        } catch (Exception e) {
            log.error("[SAGA][Step4:ExecuteTransfer] Falha. transferId={}, erro={}", transferId, e.getMessage());
            if (context.isTransferExecuted()) {
                log.warn("[SAGA] Iniciando compensação do Step4 e Step2 para transferId={}", transferId);
                compensateTransfer(context);
            }
            updateTransferStatus(transfer, TransferStatus.FAILED);
            throw new ExternalServiceException("Transfer execution failed: " + e.getMessage(), e);
        }

        try {
            publishBacenEventStep.execute(context);
        } catch (Exception e) {
            log.error("[SAGA][Step5:PublishBacenEvent] Falha. Iniciando compensação para transferId={}", transferId);
            compensateTransfer(context);
            updateTransferStatus(transfer, TransferStatus.ROLLED_BACK);
            log.error("[AUDIT] SAGA ROLLED_BACK transferId={} — motivo: falha ao publicar evento BACEN. erro={}", transferId, e.getMessage());
            throw new ExternalServiceException("Failed to publish BACEN event, transfer rolled back: " + e.getMessage(), e);
        }

        context.setSagaStatus(TransferStatus.PROCESSING);
        log.info("[SAGA] Concluído com sucesso transferId={}", transferId);
        return transfer;
    }

    private void compensateTransfer(SagaContext context) {
        try {
            executeTransferStep.compensate(context);
        } catch (Exception e) {
            log.error("[SAGA][Compensate][Step4] Falha na compensação do ExecuteTransfer transferId={}: {}", context.getTransferId(), e.getMessage());
        }
        try {
            validateLimitStep.compensate(context);
        } catch (Exception e) {
            log.error("[SAGA][Compensate][Step3] Falha na compensação do ValidateLimit transferId={}: {}", context.getTransferId(), e.getMessage());
        }
    }

    private void updateTransferStatus(Transfer transfer, TransferStatus status) {
        transfer.markAs(status);
        transferPort.save(transfer);
    }
}
