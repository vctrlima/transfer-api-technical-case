package com.personal.transfer.application.saga.steps;

import com.personal.transfer.application.dto.CustomerInfo;
import com.personal.transfer.application.ports.out.CustomerGateway;
import com.personal.transfer.application.saga.SagaContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FetchCustomerStep — busca de dados do cliente via API de Cadastro")
class FetchCustomerStepTest {

    @Mock
    private CustomerGateway cadastroApiPort;

    @InjectMocks
    private FetchCustomerStep fetchCustomerStep;

    private SagaContext buildContext() {
        return SagaContext.builder()
                .transferId("transfer-001")
                .originAccountId("acc-origin")
                .destinationAccountId("acc-dest")
                .amount(new BigDecimal("100.00"))
                .build();
    }

    @Test
    @DisplayName("execute → busca cliente por originAccountId e armazena no contexto")
    void givenValidAccount_whenExecute_thenFetchesCustomerAndSetsInContext() {
        SagaContext context = buildContext();
        CustomerInfo customer = new CustomerInfo("acc-origin", "Victor Lima", "ACTIVE");
        when(cadastroApiPort.fetchCustomer("acc-origin")).thenReturn(customer);

        assertThatCode(() -> fetchCustomerStep.execute(context)).doesNotThrowAnyException();

        assertThat(context.getCustomer()).isEqualTo(customer);
        assertThat(context.getCustomer().name()).isEqualTo("Victor Lima");
        verify(cadastroApiPort).fetchCustomer("acc-origin");
    }

    @Test
    @DisplayName("execute → propaga exceção da API de Cadastro sem modificar contexto")
    void givenApiThrows_whenExecute_thenPropagatesException() {
        SagaContext context = buildContext();
        when(cadastroApiPort.fetchCustomer("acc-origin"))
                .thenThrow(new RuntimeException("Cadastro unavailable"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fetchCustomerStep.execute(context))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cadastro unavailable");

        assertThat(context.getCustomer()).isNull();
    }

    @Test
    @DisplayName("compensate → operação no-op, nenhuma chamada à API de Cadastro")
    void whenCompensate_thenNoInteractionWithCadastroApi() {
        SagaContext context = buildContext();

        assertThatCode(() -> fetchCustomerStep.compensate(context)).doesNotThrowAnyException();

        verifyNoInteractions(cadastroApiPort);
    }
}

