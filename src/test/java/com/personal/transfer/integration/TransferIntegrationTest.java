package com.personal.transfer.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("TransferController — Testes de integração")
class TransferIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TRANSFER_URL = "/v1/transfers";

    private void stubCadastroSuccess() {
        CADASTRO_MOCK.stubFor(get(urlEqualTo("/customers/" + "acc-origin-001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + "acc-origin-001" + "\",\"name\":\"" + "Victor Lima" + "\",\"status\":\"ACTIVE\"}")));
    }

    private String transferBody(String origin, String dest, String amount) {
        return "{\"originAccountId\":\"" + origin + "\","
                + "\"destinationAccountId\":\"" + dest + "\","
                + "\"amount\":" + amount + ","
                + "\"description\":\"Test transfer\"}";
    }

    @Nested
    @DisplayName("Fluxo feliz")
    class HappyPath {

        @Test
        @DisplayName("transferência válida → 202 ACCEPTED com status PROCESSING")
        void givenValidTransfer_whenPost_thenReturns202Processing() throws Exception {
            stubCadastroSuccess();

            mockMvc.perform(post(TRANSFER_URL)
                            .header("Idempotency-Key", "idem-happy-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transferBody("acc-origin-001", "acc-dest-001", "200.00")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("PROCESSING"))
                    .andExpect(jsonPath("$.transferId").isNotEmpty())
                    .andExpect(jsonPath("$.originAccountId").value("acc-origin-001"))
                    .andExpect(jsonPath("$.amount").value(200.00));
        }
    }

    @Nested
    @DisplayName("Validações de negócio")
    class BusinessValidations {

        @Test
        @DisplayName("conta de origem INACTIVE → 422 ACCOUNT_INACTIVE")
        void givenInactiveOriginAccount_whenPost_thenReturns422() throws Exception {
            mockMvc.perform(post(TRANSFER_URL)
                            .header("Idempotency-Key", "idem-inactive-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transferBody("acc-inactive-001", "acc-dest-001", "100.00")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
        }

        @Test
        @DisplayName("conta de destino BLOCKED → 422 ACCOUNT_INACTIVE")
        void givenBlockedDestinationAccount_whenPost_thenReturns422() throws Exception {
            mockMvc.perform(post(TRANSFER_URL)
                            .header("Idempotency-Key", "idem-blocked-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transferBody("acc-origin-001", "acc-blocked-001", "100.00")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
        }

        @Test
        @DisplayName("saldo insuficiente → 422 INSUFFICIENT_BALANCE")
        void givenInsufficientBalance_whenPost_thenReturns422() throws Exception {
            mockMvc.perform(post(TRANSFER_URL)
                            .header("Idempotency-Key", "idem-insufficient-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transferBody("acc-origin-001", "acc-dest-001", "9999.00")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
        }

        @Test
        @DisplayName("limite diário excedido → 422 DAILY_LIMIT_EXCEEDED")
        void givenDailyLimitExceeded_whenPost_thenReturns422() throws Exception {
            Mockito.when(dailyLimitRepository.incrementAndGet(
                            ArgumentMatchers.eq("acc-origin-001"),
                            ArgumentMatchers.any(BigDecimal.class)))
                    .thenReturn(new BigDecimal("1100.00"));

            mockMvc.perform(post(TRANSFER_URL)
                            .header("Idempotency-Key", "idem-limit-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transferBody("acc-origin-001", "acc-dest-001", "200.00")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"));
        }

        @Test
        @DisplayName("Idempotency-Key ausente → 400 MISSING_IDEMPOTENCY_KEY")
        void givenMissingIdempotencyKey_whenPost_thenReturns400() throws Exception {
            mockMvc.perform(post(TRANSFER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transferBody("acc-origin-001", "acc-dest-001", "100.00")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"));
        }

        @Test
        @DisplayName("amount = 0 → 400 VALIDATION_ERROR")
        void givenZeroAmount_whenPost_thenReturns400() throws Exception {
            mockMvc.perform(post(TRANSFER_URL)
                            .header("Idempotency-Key", "idem-zero-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transferBody("acc-origin-001", "acc-dest-001", "0.00")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Cenário BACEN 429")
    class BacenRateLimit {

        @Test
        @DisplayName("BACEN retorna 429 → transferência criada com status PROCESSING (notificação re-enfileirada via SQS)")
        void givenBacenRateLimit_whenPost_thenTransferCreatedAndPublisherCalled() throws Exception {
            stubCadastroSuccess();

            mockMvc.perform(post(TRANSFER_URL)
                            .header("Idempotency-Key", "idem-bacen-429-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transferBody("acc-origin-001", "acc-dest-001", "100.00")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("PROCESSING"));

            Mockito.verify(bacenEventPublisher).publish(ArgumentMatchers.any());
        }
    }
}

