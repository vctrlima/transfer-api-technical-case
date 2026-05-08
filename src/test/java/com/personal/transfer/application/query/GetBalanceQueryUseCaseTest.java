package com.personal.transfer.application.query;

import com.personal.transfer.application.dto.BalanceResult;
import com.personal.transfer.application.usecases.BalanceUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetBalanceQueryUseCase")
class GetBalanceQueryUseCaseTest {

    @Mock
    private BalanceUseCase balanceUseCase;

    @InjectMocks
    private GetBalanceQueryUseCase getBalanceQueryUseCase;

    @Test
    @DisplayName("execute → delega consulta ao BalanceUseCase")
    void givenAccountId_whenExecute_thenDelegatesToBalanceUseCase() {
        BalanceResult expected = new BalanceResult(
                "acc-001",
                "Victor Lima",
                new BigDecimal("1500.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("200.00"),
                new BigDecimal("800.00"),
                LocalDateTime.now()
        );
        when(balanceUseCase.getBalance("acc-001")).thenReturn(expected);

        BalanceResult result = getBalanceQueryUseCase.execute("acc-001");

        assertThat(result).isEqualTo(expected);
        verify(balanceUseCase).getBalance("acc-001");
    }
}
