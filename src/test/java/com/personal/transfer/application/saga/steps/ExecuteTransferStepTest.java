package com.personal.transfer.application.saga.steps;

import com.personal.transfer.application.ports.out.AccountPort;
import com.personal.transfer.application.ports.out.BalanceCachePort;
import com.personal.transfer.application.ports.out.TransferPort;
import com.personal.transfer.application.saga.SagaContext;
import com.personal.transfer.domain.entities.Account;
import com.personal.transfer.domain.entities.AccountStatus;
import com.personal.transfer.domain.entities.Transfer;
import com.personal.transfer.domain.entities.TransferStatus;
import com.personal.transfer.domain.exceptions.AccountNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecuteTransferStep — débito/crédito e compensação transacional")
class ExecuteTransferStepTest {

    @Mock
    private AccountPort accountRepository;

    @Mock
    private BalanceCachePort balanceCacheRepository;

    @Mock
    private TransferPort transferRepository;

    @InjectMocks
    private ExecuteTransferStep executeTransferStep;

    private SagaContext context;
    private Account origin;
    private Account destination;

    @BeforeEach
    void setUp() {
        context = SagaContext.builder()
                .transferId("transfer-001")
                .originAccountId("acc-origin")
                .destinationAccountId("acc-dest")
                .amount(new BigDecimal("200.00"))
                .build();

        origin = Account.builder()
                .id("acc-origin")
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("1000.00"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        destination = Account.builder()
                .id("acc-dest")
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("500.00"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("execute()")
    class Execute {

        @Test
        @DisplayName("fluxo feliz com pendingTransfer → débita origem, credita destino, persiste transferência")
        void givenValidAccounts_whenExecute_thenDebitAndCreditAndPersist() {
            Transfer pendingTransfer = Transfer.builder()
                    .id("transfer-001")
                    .status(TransferStatus.PROCESSING)
                    .idempotencyKey("key-001")
                    .build();
            context.setPendingTransfer(pendingTransfer);
            when(accountRepository.findAllByIdsWithLock(anyList())).thenReturn(List.of(origin, destination));

            assertThatCode(() -> executeTransferStep.execute(context)).doesNotThrowAnyException();

            assertThat(origin.getBalance()).isEqualByComparingTo("800.00");
            assertThat(destination.getBalance()).isEqualByComparingTo("700.00");
            assertThat(context.isTransferExecuted()).isTrue();
            verify(accountRepository).saveAll(anyList());
            verify(transferRepository).save(pendingTransfer);
            verify(balanceCacheRepository).evictAll("acc-origin", "acc-dest");
        }

        @Test
        @DisplayName("pendingTransfer = null → não chama transferRepository.save()")
        void givenNoPendingTransfer_whenExecute_thenSkipsTransferPersistence() {
            context.setPendingTransfer(null);
            when(accountRepository.findAllByIdsWithLock(anyList())).thenReturn(List.of(origin, destination));

            assertThatCode(() -> executeTransferStep.execute(context)).doesNotThrowAnyException();

            verify(transferRepository, never()).save(any());
            assertThat(context.isTransferExecuted()).isTrue();
        }

        @Test
        @DisplayName("conta de origem não encontrada → lançar AccountNotFoundException")
        void givenOriginAccountNotFound_whenExecute_thenThrowsAccountNotFoundException() {
            when(accountRepository.findAllByIdsWithLock(anyList())).thenReturn(List.of(destination));

            assertThatThrownBy(() -> executeTransferStep.execute(context))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("acc-origin");
        }

        @Test
        @DisplayName("conta de destino não encontrada → lançar AccountNotFoundException")
        void givenDestinationAccountNotFound_whenExecute_thenThrowsAccountNotFoundException() {
            when(accountRepository.findAllByIdsWithLock(anyList())).thenReturn(List.of(origin));

            assertThatThrownBy(() -> executeTransferStep.execute(context))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("acc-dest");
        }
    }

    @Nested
    @DisplayName("compensate()")
    class Compensate {

        @Test
        @DisplayName("transferExecuted = false → retorna sem realizar nenhuma ação")
        void givenTransferNotExecuted_whenCompensate_thenNoAction() {
            context.setTransferExecuted(false);

            assertThatCode(() -> executeTransferStep.compensate(context)).doesNotThrowAnyException();

            verify(accountRepository, never()).saveAll(anyList());
            verify(balanceCacheRepository, never()).evictAll(any());
        }

        @Test
        @DisplayName("transferExecuted = true → credita origem, debita destino (reversão)")
        void givenTransferExecuted_whenCompensate_thenReversesDebitCredit() {
            context.setTransferExecuted(true);
            when(accountRepository.findAllByIdsWithLock(anyList())).thenReturn(List.of(origin, destination));

            assertThatCode(() -> executeTransferStep.compensate(context)).doesNotThrowAnyException();

            assertThat(origin.getBalance()).isEqualByComparingTo("1200.00");
            assertThat(destination.getBalance()).isEqualByComparingTo("300.00");
            verify(accountRepository).saveAll(anyList());
            verify(balanceCacheRepository).evictAll("acc-origin", "acc-dest");
        }

        @Test
        @DisplayName("conta de origem não encontrada na compensação → lançar AccountNotFoundException")
        void givenOriginNotFoundDuringCompensation_whenCompensate_thenThrowsAccountNotFoundException() {
            context.setTransferExecuted(true);
            when(accountRepository.findAllByIdsWithLock(anyList())).thenReturn(List.of(destination));

            assertThatThrownBy(() -> executeTransferStep.compensate(context))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("acc-origin");
        }

        @Test
        @DisplayName("conta de destino não encontrada na compensação → lançar AccountNotFoundException")
        void givenDestinationNotFoundDuringCompensation_whenCompensate_thenThrowsAccountNotFoundException() {
            context.setTransferExecuted(true);
            when(accountRepository.findAllByIdsWithLock(anyList())).thenReturn(List.of(origin));

            assertThatThrownBy(() -> executeTransferStep.compensate(context))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("acc-dest");
        }
    }
}

