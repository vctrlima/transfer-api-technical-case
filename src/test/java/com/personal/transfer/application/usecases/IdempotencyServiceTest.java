package com.personal.transfer.application.usecases;

import com.personal.transfer.application.dto.TransferResult;
import com.personal.transfer.application.ports.out.IdempotencyPort;
import com.personal.transfer.domain.entities.TransferStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SPEC-UT-05: Idempotência (RN-05)")
class IdempotencyServiceTest {

    @Mock
    private IdempotencyPort idempotencyRepository;

    @Test
    @DisplayName("chave nova → retornar empty, prosseguir fluxo")
    void givenNewKey_whenCheck_thenReturnEmpty() {
        String key = "new-key-001";
        when(idempotencyRepository.findTransferResult(key)).thenReturn(Optional.empty());

        Optional<TransferResult> result = idempotencyRepository.findTransferResult(key);

        assertThat(result).isEmpty();
        verify(idempotencyRepository, times(1)).findTransferResult(key);
    }

    @Test
    @DisplayName("chave já processada → retornar resultado original sem reprocessar")
    void givenExistingKey_whenCheck_thenReturnCachedResponse() {
        String key = "existing-key-001";
        TransferResult cachedResponse = transferResult("t-001");
        when(idempotencyRepository.findTransferResult(key)).thenReturn(Optional.of(cachedResponse));

        Optional<TransferResult> result = idempotencyRepository.findTransferResult(key);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(cachedResponse);
    }

    @Test
    @DisplayName("salvar chave nova com TTL 24h")
    void givenNewKey_whenSave_thenPersistsWithTTL() {
        String key = "key-to-save";
        TransferResult response = transferResult("t-002");

        idempotencyRepository.saveTransferResult(key, response);

        verify(idempotencyRepository, times(1)).saveTransferResult(key, response);
    }

    @Test
    @DisplayName("chave expirada (TTL) → tratar como chave nova")
    void givenExpiredKey_whenCheck_thenReturnEmpty() {
        String expiredKey = "expired-key-001";
        when(idempotencyRepository.findTransferResult(expiredKey)).thenReturn(Optional.empty());

        Optional<TransferResult> result = idempotencyRepository.findTransferResult(expiredKey);

        assertThat(result).isEmpty();
    }

    private TransferResult transferResult(String transferId) {
        return new TransferResult(
                transferId,
                TransferStatus.PROCESSING,
                new BigDecimal("10.00"),
                "acc-origin",
                "acc-dest",
                LocalDateTime.now()
        );
    }
}
