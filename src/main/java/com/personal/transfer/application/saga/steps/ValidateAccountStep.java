package com.personal.transfer.application.saga.steps;

import com.personal.transfer.application.ports.out.AccountPort;
import com.personal.transfer.application.saga.SagaContext;
import com.personal.transfer.application.saga.SagaStep;
import com.personal.transfer.domain.entities.Account;
import com.personal.transfer.domain.exceptions.AccountNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ValidateAccountStep implements SagaStep<SagaContext> {

    private final AccountPort accountPort;

    @Override
    public void execute(SagaContext context) {
        log.info("[SAGA][Step2:ValidateAccount] Iniciando para transferId={}", context.getTransferId());

        List<Account> accounts = accountPort.findAllByIds(
                List.of(context.getOriginAccountId(), context.getDestinationAccountId()));

        Map<String, Account> byId = accounts.stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        Account origin = byId.get(context.getOriginAccountId());
        if (origin == null) {
            throw new AccountNotFoundException(context.getOriginAccountId());
        }

        origin.ensureActive();
        origin.ensureSufficientBalance(context.getAmount());

        Account destination = byId.get(context.getDestinationAccountId());
        if (destination == null) {
            throw new AccountNotFoundException(context.getDestinationAccountId());
        }

        destination.ensureActive();

        log.info("[SAGA][Step2:ValidateAccount] Validação aprovada para transferId={}", context.getTransferId());
    }

    @Override
    public void compensate(SagaContext context) {
        log.info("[SAGA][Step2:ValidateAccount][Compensate] Nenhuma ação necessária para transferId={}", context.getTransferId());
    }
}
