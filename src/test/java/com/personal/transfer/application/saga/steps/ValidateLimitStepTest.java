package com.personal.transfer.application.saga.steps;

import com.personal.transfer.application.ports.out.DailyLimitPort;
import com.personal.transfer.application.saga.SagaContext;
import com.personal.transfer.domain.exceptions.DailyLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SPEC-UT-04: Limite Diário (RN-04)")
class ValidateLimitStepTest {

    @Mock
    private DailyLimitPort dailyLimitRepository;

    @InjectMocks
    private ValidateLimitStep validateLimitStep;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(validateLimitStep, "dailyLimit", new BigDecimal("1000.00"));
    }

    private SagaContext buildContext(BigDecimal amount) {
        return SagaContext.builder()
                .transferId("transfer-001")
                .originAccountId("acc-001")
                .amount(amount)
                .build();
    }

    @Test
    @DisplayName("acumulado = 0, amount = 1000 → aprovar (limite exato)")
    void givenZeroAccumulatedAndExactLimit_whenExecute_thenApproved() {
        BigDecimal amount = new BigDecimal("1000.00");
        when(dailyLimitRepository.incrementAndGet(eq("acc-001"), eq(amount)))
                .thenReturn(new BigDecimal("1000.00"));

        assertThatCode(() -> validateLimitStep.execute(buildContext(amount)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("acumulado = 0, amount = 1001 → lançar DailyLimitExceededException")
    void givenZeroAccumulatedAndExceedsLimit_whenExecute_thenThrowsDailyLimitExceeded() {
        BigDecimal amount = new BigDecimal("1001.00");
        when(dailyLimitRepository.incrementAndGet(eq("acc-001"), eq(amount)))
                .thenReturn(new BigDecimal("1001.00"));

        assertThatThrownBy(() -> validateLimitStep.execute(buildContext(amount)))
                .isInstanceOf(DailyLimitExceededException.class);

        verify(dailyLimitRepository).decrement(eq("acc-001"), eq(amount));
    }

    @Test
    @DisplayName("acumulado = 600, amount = 400 → aprovar (soma exata)")
    void givenPartialAccumulatedAndExactRemainder_whenExecute_thenApproved() {
        BigDecimal amount = new BigDecimal("400.00");
        when(dailyLimitRepository.incrementAndGet(eq("acc-001"), eq(amount)))
                .thenReturn(new BigDecimal("1000.00"));

        assertThatCode(() -> validateLimitStep.execute(buildContext(amount)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("acumulado = 600, amount = 401 → lançar DailyLimitExceededException")
    void givenPartialAccumulatedAndExceedsRemainder_whenExecute_thenThrowsDailyLimitExceeded() {
        BigDecimal amount = new BigDecimal("401.00");
        when(dailyLimitRepository.incrementAndGet(eq("acc-001"), eq(amount)))
                .thenReturn(new BigDecimal("1001.00"));

        assertThatThrownBy(() -> validateLimitStep.execute(buildContext(amount)))
                .isInstanceOf(DailyLimitExceededException.class);

        verify(dailyLimitRepository).decrement(eq("acc-001"), eq(amount));
    }

    @Test
    @DisplayName("acumulado = 1000, amount = 1 → lançar DailyLimitExceededException")
    void givenFullAccumulatedAndAnyAmount_whenExecute_thenThrowsDailyLimitExceeded() {
        BigDecimal amount = new BigDecimal("1.00");
        when(dailyLimitRepository.incrementAndGet(eq("acc-001"), eq(amount)))
                .thenReturn(new BigDecimal("1001.00"));

        assertThatThrownBy(() -> validateLimitStep.execute(buildContext(amount)))
                .isInstanceOf(DailyLimitExceededException.class);

        verify(dailyLimitRepository).decrement(eq("acc-001"), eq(amount));
    }

    @Test
    @DisplayName("compensate → decrementa o valor no Redis para reverter o incremento")
    void givenContext_whenCompensate_thenDecrementsRedisCounter() {
        BigDecimal amount = new BigDecimal("300.00");
        SagaContext context = buildContext(amount);

        assertThatCode(() -> validateLimitStep.compensate(context)).doesNotThrowAnyException();

        verify(dailyLimitRepository).decrement(eq("acc-001"), eq(amount));
    }
}

