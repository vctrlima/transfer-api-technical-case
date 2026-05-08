package com.personal.transfer.application.usecases;

import com.personal.transfer.application.saga.SagaContext;
import com.personal.transfer.application.saga.TransferSagaOrchestrator;
import com.personal.transfer.domain.entities.Transfer;
import com.personal.transfer.domain.entities.TransferStatus;
import com.personal.transfer.domain.exceptions.ExternalServiceException;
import com.personal.transfer.domain.valueobjects.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferUseCase — orquestração da transferência via SAGA")
class TransferUseCaseTest {

    @Mock
    private TransferSagaOrchestrator sagaOrchestrator;

    @InjectMocks
    private TransferUseCase transferUseCase;

    @Test
    @DisplayName("execute → delega à SAGA com SagaContext correto e retorna Transfer")
    void givenValidInput_whenExecute_thenDelegatesToSagaWithCorrectContextAndReturnsTransfer() {
        Transfer expected = Transfer.builder()
                .id("transfer-001")
                .originAccountId("acc-origin")
                .destinationAccountId("acc-dest")
                .amount(new BigDecimal("200.00"))
                .status(TransferStatus.PROCESSING)
                .idempotencyKey("idem-key-001")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(sagaOrchestrator.execute(any(SagaContext.class))).thenReturn(expected);

        Transfer result = transferUseCase.execute(
                "acc-origin",
                "acc-dest",
                new Money(new BigDecimal("200.00")),
                "idem-key-001",
                "Pagamento de serviço"
        );

        assertThat(result).isEqualTo(expected);

        ArgumentCaptor<SagaContext> contextCaptor = ArgumentCaptor.forClass(SagaContext.class);
        verify(sagaOrchestrator).execute(contextCaptor.capture());

        SagaContext capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getOriginAccountId()).isEqualTo("acc-origin");
        assertThat(capturedContext.getDestinationAccountId()).isEqualTo("acc-dest");
        assertThat(capturedContext.getAmount()).isEqualByComparingTo("200.00");
        assertThat(capturedContext.getIdempotencyKey()).isEqualTo("idem-key-001");
        assertThat(capturedContext.getDescription()).isEqualTo("Pagamento de serviço");
    }

    @Test
    @DisplayName("execute sem description → SagaContext com description = null")
    void givenNullDescription_whenExecute_thenContextHasNullDescription() {
        Transfer expected = Transfer.builder()
                .id("transfer-002")
                .status(TransferStatus.PROCESSING)
                .idempotencyKey("idem-key-002")
                .build();

        when(sagaOrchestrator.execute(any(SagaContext.class))).thenReturn(expected);

        transferUseCase.execute(
                "acc-origin",
                "acc-dest",
                new Money(new BigDecimal("50.00")),
                "idem-key-002",
                null
        );

        ArgumentCaptor<SagaContext> contextCaptor = ArgumentCaptor.forClass(SagaContext.class);
        verify(sagaOrchestrator).execute(contextCaptor.capture());

        assertThat(contextCaptor.getValue().getDescription()).isNull();
    }

    @Test
    @DisplayName("execute → propaga exceção lançada pela SAGA")
    void givenSagaThrows_whenExecute_thenPropagatesException() {
        when(sagaOrchestrator.execute(any(SagaContext.class)))
                .thenThrow(new ExternalServiceException("SAGA execution failed"));

        assertThatThrownBy(() -> transferUseCase.execute(
                "acc-origin",
                "acc-dest",
                new Money(new BigDecimal("100.00")),
                "idem-key-003",
                null
        )).isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("SAGA execution failed");
    }
}

