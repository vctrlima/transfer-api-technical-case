package com.personal.transfer.application.usecases;

import com.personal.transfer.application.dto.TransferResult;
import com.personal.transfer.application.ports.out.IdempotencyPort;
import com.personal.transfer.application.saga.SagaContext;
import com.personal.transfer.application.saga.TransferSagaOrchestrator;
import com.personal.transfer.domain.entities.Transfer;
import com.personal.transfer.domain.valueobjects.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferUseCase {

    private final TransferSagaOrchestrator sagaOrchestrator;
    private final IdempotencyPort idempotencyPort;

    public TransferResult execute(String originAccountId,
                                  String destinationAccountId,
                                  Money amount,
                                  String idempotencyKey,
                                  String description) {
        var cached = idempotencyPort.findTransferResult(idempotencyKey);
        if (cached.isPresent()) {
            log.info("Idempotent response returned for key={}", idempotencyKey);
            return cached.get();
        }

        SagaContext context = SagaContext.builder()
                .originAccountId(originAccountId)
                .destinationAccountId(destinationAccountId)
                .amount(amount.value())
                .idempotencyKey(idempotencyKey)
                .description(description)
                .build();

        Transfer transfer = sagaOrchestrator.execute(context);
        TransferResult response = TransferResult.from(transfer);
        idempotencyPort.saveTransferResult(idempotencyKey, response);
        return response;
    }
}
