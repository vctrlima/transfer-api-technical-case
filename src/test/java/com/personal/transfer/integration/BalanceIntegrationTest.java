package com.personal.transfer.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("BalanceController — Testes de integração")
class BalanceIntegrationTest extends BaseIntegrationTest {

    private static final String BALANCE_URL = "/v1/accounts/{accountId}/balance";

    private void stubCadastroCustomer() {
        CADASTRO_MOCK.stubFor(WireMock.get(WireMock.urlEqualTo("/customers/" + "acc-origin-001"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + "acc-origin-001" + "\",\"name\":\"" + "Victor Lima" + "\",\"status\":\"ACTIVE\"}")));
    }

    @Nested
    @DisplayName("Fluxo feliz")
    class HappyPath {

        @Test
        @DisplayName("conta ACTIVE → 200 com saldo e customerName da API de Cadastro")
        void givenActiveAccount_whenGetBalance_thenReturns200WithCustomerName() throws Exception {
            stubCadastroCustomer();

            mockMvc.perform(get(BALANCE_URL, "acc-origin-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId").value("acc-origin-001"))
                    .andExpect(jsonPath("$.customerName").value("Victor Lima"))
                    .andExpect(jsonPath("$.balance").value(1500.00))
                    .andExpect(jsonPath("$.availableLimit").value(1000.00))
                    .andExpect(jsonPath("$.dailyLimitUsed").value(0))
                    .andExpect(jsonPath("$.dailyLimitRemaining").value(1000.00));
        }

        @Test
        @DisplayName("limite diário parcialmente utilizado → dailyLimitRemaining correto")
        void givenPartialDailyLimitUsed_whenGetBalance_thenCorrectRemaining() throws Exception {
            stubCadastroCustomer();
            org.mockito.Mockito.when(dailyLimitRepository.getAccumulated("acc-origin-001"))
                    .thenReturn(new BigDecimal("600.00"));

            mockMvc.perform(get(BALANCE_URL, "acc-origin-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dailyLimitUsed").value(600.00))
                    .andExpect(jsonPath("$.dailyLimitRemaining").value(400.00));
        }
    }

    @Nested
    @DisplayName("Validações de negócio")
    class BusinessValidations {

        @Test
        @DisplayName("conta INACTIVE → 422 ACCOUNT_INACTIVE (novas regra)")
        void givenInactiveAccount_whenGetBalance_thenReturns422() throws Exception {
            mockMvc.perform(get(BALANCE_URL, "acc-inactive-001"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
        }

        @Test
        @DisplayName("conta BLOCKED → 422 ACCOUNT_INACTIVE")
        void givenBlockedAccount_whenGetBalance_thenReturns422() throws Exception {
            mockMvc.perform(get(BALANCE_URL, "acc-blocked-001"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
        }

        @Test
        @DisplayName("conta inexistente → 422 ENTITY_NOT_FOUND")
        void givenNonExistentAccount_whenGetBalance_thenReturns422() throws Exception {
            mockMvc.perform(get(BALANCE_URL, "acc-inexistente-999"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));
        }
    }
}

