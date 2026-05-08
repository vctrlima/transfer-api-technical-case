package com.personal.transfer.application.saga.steps;

import com.personal.transfer.application.saga.SagaContext;
import com.personal.transfer.infrastructure.sqs.BacenEventPublisher;
import com.personal.transfer.infrastructure.sqs.BacenTransferEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PublishBacenEventStep — publicação de evento de transferência no SQS")
class PublishBacenEventStepTest {

    @Mock
    private BacenEventPublisher bacenEventPublisher;

    @InjectMocks
    private PublishBacenEventStep publishBacenEventStep;

    private SagaContext buildContext() {
        return SagaContext.builder()
                .transferId("transfer-001")
                .originAccountId("acc-origin")
                .destinationAccountId("acc-dest")
                .amount(new BigDecimal("200.00"))
                .build();
    }

    @Test
    @DisplayName("execute → cria BacenTransferEvent com dados do contexto e publica no SQS")
    void givenValidContext_whenExecute_thenPublishesEventWithCorrectData() {
        SagaContext context = buildContext();
        doNothing().when(bacenEventPublisher).publish(any(BacenTransferEvent.class));

        assertThatCode(() -> publishBacenEventStep.execute(context)).doesNotThrowAnyException();

        ArgumentCaptor<BacenTransferEvent> eventCaptor = ArgumentCaptor.forClass(BacenTransferEvent.class);
        verify(bacenEventPublisher).publish(eventCaptor.capture());

        BacenTransferEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.transferId()).isEqualTo("transfer-001");
        assertThat(capturedEvent.originAccountId()).isEqualTo("acc-origin");
        assertThat(capturedEvent.destinationAccountId()).isEqualTo("acc-dest");
        assertThat(capturedEvent.amount()).isEqualByComparingTo("200.00");
        assertThat(capturedEvent.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("execute → propaga exceção do publisher (ex: SQS indisponível)")
    void givenPublisherThrows_whenExecute_thenPropagatesException() {
        SagaContext context = buildContext();
        doThrow(new RuntimeException("SQS unavailable")).when(bacenEventPublisher).publish(any());

        assertThatThrownBy(() -> publishBacenEventStep.execute(context))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SQS unavailable");
    }

    @Test
    @DisplayName("compensate → operação no-op, nenhuma interação com o publisher")
    void whenCompensate_thenNoInteractionWithPublisher() {
        SagaContext context = buildContext();

        assertThatCode(() -> publishBacenEventStep.compensate(context)).doesNotThrowAnyException();

        verifyNoInteractions(bacenEventPublisher);
    }
}

