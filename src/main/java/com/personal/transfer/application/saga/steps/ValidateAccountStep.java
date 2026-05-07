package com.personal.transfer.application.saga.steps;

import com.personal.transfer.application.saga.SagaContext;
import com.personal.transfer.application.saga.SagaStep;
import com.personal.transfer.domain.entities.Account;
import com.personal.transfer.domain.exceptions.AccountInactiveException;
import com.personal.transfer.domain.exceptions.InsufficientBalanceException;
import com.personal.transfer.infrastructure.persistence.AccountRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ValidateAccountStep implements SagaStep<SagaContext> {

    private final AccountRepository accountRepository;

    @Override
    public void execute(SagaContext context) {
        log.info("[SAGA][Step2:ValidateAccount] Iniciando para transferId={}", context.getTransferId());

        Account origin = accountRepository
                .findById(context.getOriginAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Origin account not found: " + context.getOriginAccountId()));

        if (!origin.isActive()) {
            throw new AccountInactiveException(context.getOriginAccountId());
        }

        if (!origin.hasSufficientBalance(context.getAmount())) {
            throw new InsufficientBalanceException(origin.getBalance(), context.getAmount());
        }

        log.info("[SAGA][Step2:ValidateAccount] Validação aprovada para transferId={}", context.getTransferId());
    }

    @Override
    public void compensate(SagaContext context) {
        log.info("[SAGA][Step2:ValidateAccount][Compensate] Nenhuma ação necessária para transferId={}", context.getTransferId());
    }
}
