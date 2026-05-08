package com.personal.transfer.application.saga.steps;

import com.personal.transfer.application.dto.CustomerInfo;
import com.personal.transfer.application.ports.out.CustomerGateway;
import com.personal.transfer.application.saga.SagaContext;
import com.personal.transfer.application.saga.SagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FetchCustomerStep implements SagaStep<SagaContext> {

    private final CustomerGateway customerGateway;

    @Override
    public void execute(SagaContext context) {
        log.info("[SAGA][Step1:FetchCustomer] Iniciando para transferId={}", context.getTransferId());
        CustomerInfo customer = customerGateway.fetchCustomer(context.getOriginAccountId());
        context.setCustomer(customer);
        log.info("[SAGA][Step1:FetchCustomer] Concluído para transferId={}, customerId={}", context.getTransferId(), customer.id());
    }

    @Override
    public void compensate(SagaContext context) {
        log.info("[SAGA][Step1:FetchCustomer][Compensate] Nenhuma ação necessária para transferId={}", context.getTransferId());
    }
}
