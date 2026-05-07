package com.personal.transfer.application.saga.steps;

import com.personal.transfer.application.saga.SagaContext;
import com.personal.transfer.application.saga.SagaStep;
import com.personal.transfer.domain.exceptions.ExternalServiceException;
import com.personal.transfer.infrastructure.adapters.CadastroApiPort;
import com.personal.transfer.infrastructure.adapters.dto.CustomerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FetchCustomerStep implements SagaStep<SagaContext> {

    private final CadastroApiPort cadastroApiPort;

    @Override
    public void execute(SagaContext context) {
        log.info("[SAGA][Step1:FetchCustomer] Iniciando para transferId={}", context.getTransferId());
        CustomerResponse customer = cadastroApiPort.fetchCustomer(context.getOriginAccountId());
        context.setCustomer(customer);
        log.info("[SAGA][Step1:FetchCustomer] Concluído para transferId={}, customerId={}", context.getTransferId(), customer.id());
    }

    @Override
    public void compensate(SagaContext context) {
        log.info("[SAGA][Step1:FetchCustomer][Compensate] Nenhuma ação necessária para transferId={}", context.getTransferId());
    }
}
