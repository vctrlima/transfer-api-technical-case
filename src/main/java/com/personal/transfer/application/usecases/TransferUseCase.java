package com.personal.transfer.application.usecases;

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

    public Transfer execute(String originAccountId,
                            String destinationAccountId,
                            Money amount,
                            String idempotencyKey,
                            String description) {

        SagaContext context = SagaContext.builder()
                .originAccountId(originAccountId)
                .destinationAccountId(destinationAccountId)
                .amount(amount.value())
                .idempotencyKey(idempotencyKey)
                .description(description)
                .build();

        return sagaOrchestrator.execute(context);
    }
}
