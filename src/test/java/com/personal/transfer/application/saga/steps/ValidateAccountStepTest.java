package com.personal.transfer.application.saga.steps;

import com.personal.transfer.application.saga.SagaContext;
import com.personal.transfer.domain.entities.Account;
import com.personal.transfer.domain.entities.AccountStatus;
import com.personal.transfer.domain.exceptions.AccountInactiveException;
import com.personal.transfer.domain.exceptions.InsufficientBalanceException;
import com.personal.transfer.infrastructure.persistence.AccountRepository;
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SPEC-UT-02 e SPEC-UT-03: Validação de Conta Ativa e Saldo")
class ValidateAccountStepTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private ValidateAccountStep validateAccountStep;

    private SagaContext context;

    @BeforeEach
    void setUp() {
        context = SagaContext.builder()
                .transferId("transfer-001")
                .originAccountId("acc-001")
                .destinationAccountId("acc-002")
                .build();
    }

    private Account buildAccount(AccountStatus status, BigDecimal balance) {
        return Account.builder()
                .id("acc-001")
                .status(status)
                .balance(balance)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Account buildDestinationAccount(AccountStatus status) {
        return Account.builder()
                .id("acc-002")
                .status(status)
                .balance(new BigDecimal("100.00"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("RN-02: Validação de conta ativa")
    class AccountStatusValidation {

        @Test
        @DisplayName("status = ACTIVE → avançar para próxima etapa da SAGA")
        void givenActiveAccount_whenExecute_thenNoException() {
            context.setAmount(new BigDecimal("100.00"));
            Account origin = buildAccount(AccountStatus.ACTIVE, new BigDecimal("500.00"));
            Account destination = buildDestinationAccount(AccountStatus.ACTIVE);
            when(accountRepository.findAllByIds(anyList())).thenReturn(List.of(origin, destination));

            assertThatCode(() -> validateAccountStep.execute(context)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("status = INACTIVE → lançar AccountInactiveException")
        void givenInactiveAccount_whenExecute_thenThrowsAccountInactiveException() {
            context.setAmount(new BigDecimal("100.00"));
            Account account = buildAccount(AccountStatus.INACTIVE, new BigDecimal("500.00"));
            when(accountRepository.findAllByIds(anyList())).thenReturn(List.of(account));

            assertThatThrownBy(() -> validateAccountStep.execute(context))
                    .isInstanceOf(AccountInactiveException.class);
        }

        @Test
        @DisplayName("status = BLOCKED → lançar AccountInactiveException")
        void givenBlockedOriginAccount_whenExecute_thenThrowsAccountInactiveException() {
            context.setAmount(new BigDecimal("100.00"));
            Account account = buildAccount(AccountStatus.BLOCKED, new BigDecimal("500.00"));
            when(accountRepository.findAllByIds(anyList())).thenReturn(List.of(account));

            assertThatThrownBy(() -> validateAccountStep.execute(context))
                    .isInstanceOf(AccountInactiveException.class);
        }

        @Test
        @DisplayName("destino status = INACTIVE → lançar AccountInactiveException")
        void givenInactiveDestinationAccount_whenExecute_thenThrowsAccountInactiveException() {
            context.setAmount(new BigDecimal("100.00"));
            Account origin = buildAccount(AccountStatus.ACTIVE, new BigDecimal("500.00"));
            Account destination = buildDestinationAccount(AccountStatus.INACTIVE);
            when(accountRepository.findAllByIds(anyList())).thenReturn(List.of(origin, destination));

            assertThatThrownBy(() -> validateAccountStep.execute(context))
                    .isInstanceOf(AccountInactiveException.class);
        }

        @Test
        @DisplayName("destino status = BLOCKED → lançar AccountInactiveException")
        void givenBlockedDestinationAccount_whenExecute_thenThrowsAccountInactiveException() {
            context.setAmount(new BigDecimal("100.00"));
            Account origin = buildAccount(AccountStatus.ACTIVE, new BigDecimal("500.00"));
            Account destination = buildDestinationAccount(AccountStatus.BLOCKED);
            when(accountRepository.findAllByIds(anyList())).thenReturn(List.of(origin, destination));

            assertThatThrownBy(() -> validateAccountStep.execute(context))
                    .isInstanceOf(AccountInactiveException.class);
        }
    }

    @Nested
    @DisplayName("RN-03: Validação de saldo")
    class BalanceValidation {

        @Test
        @DisplayName("saldo = 500, amount = 300 → aprovar")
        void givenSufficientBalance_whenExecute_thenNoException() {
            context.setAmount(new BigDecimal("300.00"));
            Account origin = buildAccount(AccountStatus.ACTIVE, new BigDecimal("500.00"));
            Account destination = buildDestinationAccount(AccountStatus.ACTIVE);
            when(accountRepository.findAllByIds(anyList())).thenReturn(List.of(origin, destination));

            assertThatCode(() -> validateAccountStep.execute(context)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("saldo = 500, amount = 500 → aprovar (limite exato)")
        void givenExactBalance_whenExecute_thenNoException() {
            context.setAmount(new BigDecimal("500.00"));
            Account origin = buildAccount(AccountStatus.ACTIVE, new BigDecimal("500.00"));
            Account destination = buildDestinationAccount(AccountStatus.ACTIVE);
            when(accountRepository.findAllByIds(anyList())).thenReturn(List.of(origin, destination));

            assertThatCode(() -> validateAccountStep.execute(context)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("saldo = 500, amount = 501 → lançar InsufficientBalanceException")
        void givenInsufficientBalance_whenExecute_thenThrowsInsufficientBalanceException() {
            context.setAmount(new BigDecimal("501.00"));
            Account origin = buildAccount(AccountStatus.ACTIVE, new BigDecimal("500.00"));
            when(accountRepository.findAllByIds(anyList())).thenReturn(List.of(origin));

            assertThatThrownBy(() -> validateAccountStep.execute(context))
                    .isInstanceOf(InsufficientBalanceException.class);
        }

        @Test
        @DisplayName("saldo = 0, amount = 1 → lançar InsufficientBalanceException")
        void givenZeroBalance_whenExecute_thenThrowsInsufficientBalanceException() {
            context.setAmount(new BigDecimal("1.00"));
            Account origin = buildAccount(AccountStatus.ACTIVE, BigDecimal.ZERO);
            when(accountRepository.findAllByIds(anyList())).thenReturn(List.of(origin));

            assertThatThrownBy(() -> validateAccountStep.execute(context))
                    .isInstanceOf(InsufficientBalanceException.class);
        }
    }
}
