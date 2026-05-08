package com.personal.transfer.application.usecases;

import com.personal.transfer.application.dto.BalanceResult;
import com.personal.transfer.application.dto.CustomerInfo;
import com.personal.transfer.application.ports.out.AccountPort;
import com.personal.transfer.application.ports.out.BalanceCachePort;
import com.personal.transfer.application.ports.out.CustomerGateway;
import com.personal.transfer.application.ports.out.DailyLimitPort;
import com.personal.transfer.domain.entities.Account;
import com.personal.transfer.domain.entities.AccountStatus;
import com.personal.transfer.domain.exceptions.AccountInactiveException;
import com.personal.transfer.domain.exceptions.AccountNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BalanceUseCase — Consulta de saldo com validação e enriquecimento")
class BalanceUseCaseTest {

    @Mock
    private AccountPort accountRepository;

    @Mock
    private BalanceCachePort balanceCacheRepository;

    @Mock
    private DailyLimitPort dailyLimitRepository;

    @Mock
    private CustomerGateway cadastroApiPort;

    @InjectMocks
    private BalanceUseCase balanceUseCase;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(balanceUseCase, "dailyLimit", new BigDecimal("1000.00"));
        when(balanceCacheRepository.get(anyString())).thenReturn(Optional.empty());
    }

    private Account buildAccount(String id, AccountStatus status, BigDecimal balance) {
        return Account.builder()
                .id(id)
                .status(status)
                .balance(balance)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Validação de conta ativa")
    class AccountActiveValidation {

        @Test
        @DisplayName("conta INACTIVE → lançar AccountInactiveException")
        void givenInactiveAccount_whenGetBalance_thenThrowsAccountInactiveException() {
            Account account = buildAccount("acc-inactive", AccountStatus.INACTIVE, new BigDecimal("200.00"));
            when(accountRepository.findById("acc-inactive")).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> balanceUseCase.getBalance("acc-inactive"))
                    .isInstanceOf(AccountInactiveException.class)
                    .hasMessageContaining("acc-inactive");

            verifyNoInteractions(cadastroApiPort);
        }

        @Test
        @DisplayName("conta BLOCKED → lançar AccountInactiveException")
        void givenBlockedAccount_whenGetBalance_thenThrowsAccountInactiveException() {
            Account account = buildAccount("acc-blocked", AccountStatus.BLOCKED, new BigDecimal("300.00"));
            when(accountRepository.findById("acc-blocked")).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> balanceUseCase.getBalance("acc-blocked"))
                    .isInstanceOf(AccountInactiveException.class);
        }

        @Test
        @DisplayName("conta não encontrada → lançar AccountNotFoundException")
        void givenNonExistentAccount_whenGetBalance_thenThrowsAccountNotFoundException() {
            when(accountRepository.findById("acc-unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> balanceUseCase.getBalance("acc-unknown"))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("acc-unknown");
        }
    }

    @Nested
    @DisplayName("Retorno de saldo e nome do cliente")
    class BalanceReturn {

        @Test
        @DisplayName("conta ACTIVE → retornar saldo e customerName da API de Cadastro")
        void givenActiveAccount_whenGetBalance_thenReturnsBalanceWithCustomerName() {
            Account account = buildAccount("acc-001", AccountStatus.ACTIVE, new BigDecimal("1500.00"));
            when(accountRepository.findById("acc-001")).thenReturn(Optional.of(account));
            when(cadastroApiPort.fetchCustomer("acc-001"))
                    .thenReturn(new CustomerInfo("acc-001", "Victor Lima", "ACTIVE"));
            when(dailyLimitRepository.getAccumulated("acc-001")).thenReturn(new BigDecimal("200.00"));

            BalanceResult response = balanceUseCase.getBalance("acc-001");

            assertThat(response.accountId()).isEqualTo("acc-001");
            assertThat(response.customerName()).isEqualTo("Victor Lima");
            assertThat(response.balance()).isEqualByComparingTo("1500.00");
            assertThat(response.dailyLimitUsed()).isEqualByComparingTo("200.00");
            assertThat(response.dailyLimitRemaining()).isEqualByComparingTo("800.00");
        }

        @Test
        @DisplayName("limite diário totalmente consumido → dailyLimitRemaining = 0")
        void givenFullDailyLimitUsed_whenGetBalance_thenRemainingIsZero() {
            Account account = buildAccount("acc-001", AccountStatus.ACTIVE, new BigDecimal("500.00"));
            when(accountRepository.findById("acc-001")).thenReturn(Optional.of(account));
            when(cadastroApiPort.fetchCustomer("acc-001"))
                    .thenReturn(new CustomerInfo("acc-001", "Victor Lima", "ACTIVE"));
            when(dailyLimitRepository.getAccumulated("acc-001")).thenReturn(new BigDecimal("1000.00"));

            BalanceResult response = balanceUseCase.getBalance("acc-001");

            assertThat(response.dailyLimitRemaining()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Cache")
    class CacheBehavior {

        @Test
        @DisplayName("cache hit → retornar sem chamar DB ou Cadastro")
        void givenCacheHit_whenGetBalance_thenReturnsCachedResponseWithoutDbOrApiCall() throws Exception {
            BalanceResult cached = new BalanceResult(
                    "acc-001", "Victor Lima",
                    new BigDecimal("1500.00"), new BigDecimal("1000.00"),
                    new BigDecimal("200.00"), new BigDecimal("800.00"),
                    LocalDateTime.now()
            );
            when(balanceCacheRepository.get("acc-001")).thenReturn(Optional.of(cached));

            BalanceResult response = balanceUseCase.getBalance("acc-001");

            assertThat(response.customerName()).isEqualTo("Victor Lima");
            assertThat(response.balance()).isEqualByComparingTo("1500.00");
            verifyNoInteractions(accountRepository, cadastroApiPort, dailyLimitRepository);
        }
    }
}
