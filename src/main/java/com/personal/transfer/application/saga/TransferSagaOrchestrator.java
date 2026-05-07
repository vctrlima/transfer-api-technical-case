package com.personal.transfer.application.saga;

import com.personal.transfer.application.saga.steps.*;
import com.personal.transfer.domain.entities.Transfer;
import com.personal.transfer.domain.entities.TransferStatus;
import com.personal.transfer.domain.exceptions.ExternalServiceException;
import com.personal.transfer.infrastructure.persistence.TransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferSagaOrchestrator {

    private final FetchCustomerStep fetchCustomerStep;
    private final ValidateAccountStep validateAccountStep;
    private final ValidateLimitStep validateLimitStep;
    private final ExecuteTransferStep executeTransferStep;
    private final PublishBacenEventStep publishBacenEventStep;
    private final TransferRepository transferRepository;

    /**
     * Executes the SAGA with the following steps:
     * 1. ValidateAccount   — validates account status and balance (local DB)
     * 2. ValidateLimit     — validates daily limit via Redis INCRBY (atomic)
     * 3. FetchCustomer     — reads customer from Cadastro API
     * 4. ExecuteTransfer   — debits origin, credits destination
     * 5. PublishBacenEvent — publishes event to SQS
     * Compensations on failure:
     * - Step 5 fails → compensate Step 4 (rollback debit/credit) + Step 2 (Redis DECRBY)
     * - Step 4 fails before debit → no compensation
     * - Steps 1/2/3 fail → no compensation (nothing changed)
     */
    public Transfer execute(SagaContext context) {
        String transferId = UUID.randomUUID().toString();
        context.setTransferId(transferId);

        log.info("[SAGA] Iniciando para transferId={}, origin={}, destination={}, amount={}",
                transferId, context.getOriginAccountId(), context.getDestinationAccountId(), context.getAmount());

        Transfer transfer = Transfer.builder()
                .id(transferId)
                .originAccountId(context.getOriginAccountId())
                .destinationAccountId(context.getDestinationAccountId())
                .amount(context.getAmount())
                .status(TransferStatus.PROCESSING)
                .idempotencyKey(context.getIdempotencyKey())
                .description(context.getDescription())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        transferRepository.save(transfer);

        try {
            validateAccountStep.execute(context);
        } catch (Exception e) {
            log.error("[SAGA][Step1:ValidateAccount] Falha de validação. transferId={}, erro={}", transferId, e.getMessage());
            updateTransferStatus(transfer, TransferStatus.FAILED);
            throw e;
        }

        try {
            validateLimitStep.execute(context);
        } catch (Exception e) {
            log.error("[SAGA][Step2:ValidateLimit] Falha de validação. transferId={}, erro={}", transferId, e.getMessage());
            updateTransferStatus(transfer, TransferStatus.FAILED);
            throw e;
        }

        try {
            fetchCustomerStep.execute(context);
        } catch (ExternalServiceException e) {
            log.error("[SAGA][Step3:FetchCustomer] Falha — retornando 502. transferId={}, erro={}", transferId, e.getMessage());
            validateLimitStep.compensate(context);
            updateTransferStatus(transfer, TransferStatus.FAILED);
            throw e;
        }

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

        updateTransferStatus(transfer, TransferStatus.PROCESSING);
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
        transfer.setStatus(status);
        transfer.setUpdatedAt(LocalDateTime.now());
        transferRepository.save(transfer);
    }
}

