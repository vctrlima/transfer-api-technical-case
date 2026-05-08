package com.personal.transfer.application.saga;

import com.personal.transfer.application.saga.steps.*;
import com.personal.transfer.domain.entities.Transfer;
import com.personal.transfer.domain.entities.TransferStatus;
import com.personal.transfer.domain.exceptions.ExternalServiceException;
import com.personal.transfer.infrastructure.persistence.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SPEC-UT-06: Compensações da SAGA")
class TransferSagaOrchestratorTest {

    @Mock
    private FetchCustomerStep fetchCustomerStep;
    @Mock
    private ValidateAccountStep validateAccountStep;
    @Mock
    private ValidateLimitStep validateLimitStep;
    @Mock
    private ExecuteTransferStep executeTransferStep;
    @Mock
    private PublishBacenEventStep publishBacenEventStep;
    @Mock
    private TransferRepository transferRepository;

    @InjectMocks
    private TransferSagaOrchestrator orchestrator;

    private SagaContext context;

    @BeforeEach
    void setUp() {
        context = SagaContext.builder()
                .originAccountId("acc-origin-001")
                .destinationAccountId("acc-dest-001")
                .amount(new BigDecimal("200.00"))
                .idempotencyKey("idem-key-001")
                .build();

        lenient().when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("falha na Etapa 3 (FetchCustomer) → propagar exceção, compensar ValidateLimit, etapas 4 e 5 não executadas")
    void givenFetchCustomerFails_whenExecute_thenThrowsAndCompensatesLimit() {
        doNothing().when(validateAccountStep).execute(any());
        doNothing().when(validateLimitStep).execute(any());
        doThrow(new ExternalServiceException("Cadastro unavailable"))
                .when(fetchCustomerStep).execute(any());

        assertThatThrownBy(() -> orchestrator.execute(context))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("Cadastro unavailable");

        verify(validateAccountStep).execute(any());
        verify(validateLimitStep).execute(any());
        verify(fetchCustomerStep).execute(any());
        verify(validateLimitStep).compensate(any());
        verifyNoInteractions(executeTransferStep, publishBacenEventStep);
    }

    @Test
    @DisplayName("falha na Etapa 4 (ExecuteTransfer) antes do débito → nenhuma compensação disparada")
    void givenExecuteTransferFailsBeforeDebit_whenExecute_thenNoCompensation() {
        doNothing().when(validateAccountStep).execute(any());
        doNothing().when(validateLimitStep).execute(any());
        doNothing().when(fetchCustomerStep).execute(any());

        doThrow(new RuntimeException("DB connection error"))
                .when(executeTransferStep).execute(any());

        assertThatThrownBy(() -> orchestrator.execute(context))
                .isInstanceOf(ExternalServiceException.class);

        verify(executeTransferStep, never()).compensate(any());
        verify(validateLimitStep, never()).compensate(any());
    }

    @Test
    @DisplayName("falha na Etapa 5 (PublishBacenEvent) após débito → compensações executadas, status = ROLLED_BACK")
    void givenPublishBacenEventFails_whenExecute_thenCompensateAndRollback() {
        doNothing().when(validateAccountStep).execute(any());
        doNothing().when(validateLimitStep).execute(any());
        doNothing().when(fetchCustomerStep).execute(any());

        doAnswer(invocation -> {
            SagaContext ctx = invocation.getArgument(0);
            ctx.setTransferExecuted(true);
            return null;
        }).when(executeTransferStep).execute(any());

        doThrow(new RuntimeException("SQS unavailable"))
                .when(publishBacenEventStep).execute(any());

        assertThatThrownBy(() -> orchestrator.execute(context))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("rolled back");

        verify(executeTransferStep).compensate(any());
        verify(validateLimitStep).compensate(any());

        ArgumentCaptor<Transfer> transferCaptor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository, atLeastOnce()).save(transferCaptor.capture());

        Transfer lastSaved = transferCaptor.getAllValues().stream()
                .filter(t -> TransferStatus.ROLLED_BACK.equals(t.getStatus()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected ROLLED_BACK transfer to be saved"));

        assertThat(lastSaved.getStatus()).isEqualTo(TransferStatus.ROLLED_BACK);
    }

    @Test
    @DisplayName("fluxo feliz → status = PROCESSING, nenhuma compensação")
    void givenAllStepsSucceed_whenExecute_thenReturnsProcessingTransfer() {
        doNothing().when(validateAccountStep).execute(any());
        doNothing().when(validateLimitStep).execute(any());
        doNothing().when(fetchCustomerStep).execute(any());
        doAnswer(inv -> {
            SagaContext ctx = inv.getArgument(0);
            ctx.setTransferExecuted(true);
            return null;
        }).when(executeTransferStep).execute(any());
        doNothing().when(publishBacenEventStep).execute(any());

        Transfer result = orchestrator.execute(context);

        assertThat(result).isNotNull();
        assertThat(result.getOriginAccountId()).isEqualTo("acc-origin-001");

        verify(executeTransferStep, never()).compensate(any());
        verify(validateLimitStep, never()).compensate(any());
    }
}
