package com.personal.transfer.application.saga.steps;

import com.personal.transfer.application.saga.SagaContext;
import com.personal.transfer.application.saga.SagaStep;
import com.personal.transfer.infrastructure.sqs.BacenEventPublisher;
import com.personal.transfer.infrastructure.sqs.BacenTransferEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublishBacenEventStep implements SagaStep<SagaContext> {

    private final BacenEventPublisher bacenEventPublisher;

    @Override
    public void execute(SagaContext context) {
        log.info("[SAGA][Step5:PublishBacenEvent] Publicando evento SQS para transferId={}", context.getTransferId());

        BacenTransferEvent event = new BacenTransferEvent(
                context.getTransferId(),
                context.getOriginAccountId(),
                context.getDestinationAccountId(),
                context.getAmount(),
                LocalDateTime.now()
        );

        bacenEventPublisher.publish(event);
        log.info("[SAGA][Step5:PublishBacenEvent] Evento publicado com sucesso para transferId={}", context.getTransferId());
    }

    @Override
    public void compensate(SagaContext context) {
        log.info("[SAGA][Step5:PublishBacenEvent][Compensate] Nenhuma ação necessária para transferId={}", context.getTransferId());
    }
}
