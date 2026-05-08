package com.personal.transfer.application.saga.steps;

import com.personal.transfer.application.ports.out.DailyLimitPort;
import com.personal.transfer.application.saga.SagaContext;
import com.personal.transfer.application.saga.SagaStep;
import com.personal.transfer.domain.exceptions.DailyLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class ValidateLimitStep implements SagaStep<SagaContext> {

    private final DailyLimitPort dailyLimitPort;

    @Value("${transfer.daily-limit:1000.00}")
    private BigDecimal dailyLimit;

    @Override
    public void execute(SagaContext context) {
        log.info("[SAGA][Step3:ValidateLimit] Iniciando para transferId={}", context.getTransferId());

        BigDecimal newAccumulated = dailyLimitPort.incrementAndGet(
                context.getOriginAccountId(),
                context.getAmount()
        );

        if (newAccumulated.compareTo(dailyLimit) > 0) {
            dailyLimitPort.decrement(context.getOriginAccountId(), context.getAmount());
            BigDecimal accumulated = newAccumulated.subtract(context.getAmount());
            throw new DailyLimitExceededException(accumulated, context.getAmount(), dailyLimit);
        }

        log.info("[SAGA][Step3:ValidateLimit] Limite aprovado, acumulado={} para transferId={}", newAccumulated, context.getTransferId());
    }

    @Override
    public void compensate(SagaContext context) {
        log.info("[SAGA][Step3:ValidateLimit][Compensate] Revertendo incremento de limite para transferId={}", context.getTransferId());
        dailyLimitPort.decrement(context.getOriginAccountId(), context.getAmount());
    }
}
