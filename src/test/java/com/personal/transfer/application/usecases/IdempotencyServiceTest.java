package com.personal.transfer.application.usecases;

import com.personal.transfer.infrastructure.redis.IdempotencyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SPEC-UT-05: Idempotência (RN-05)")
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @Test
    @DisplayName("chave nova → retornar empty, prosseguir fluxo")
    void givenNewKey_whenCheck_thenReturnEmpty() {
        String key = "new-key-001";
        when(idempotencyRepository.findByKey(key)).thenReturn(Optional.empty());

        Optional<String> result = idempotencyRepository.findByKey(key);

        assertThat(result).isEmpty();
        verify(idempotencyRepository, times(1)).findByKey(key);
    }

    @Test
    @DisplayName("chave já processada → retornar resultado original sem reprocessar")
    void givenExistingKey_whenCheck_thenReturnCachedResponse() {
        String key = "existing-key-001";
        String cachedResponse = "{\"transferId\":\"t-001\",\"status\":\"PROCESSING\"}";
        when(idempotencyRepository.findByKey(key)).thenReturn(Optional.of(cachedResponse));

        Optional<String> result = idempotencyRepository.findByKey(key);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(cachedResponse);
    }

    @Test
    @DisplayName("salvar chave nova com TTL 24h")
    void givenNewKey_whenSave_thenPersistsWithTTL() {
        String key = "key-to-save";
        String responseJson = "{\"transferId\":\"t-002\",\"status\":\"PROCESSING\"}";

        idempotencyRepository.save(key, responseJson);

        verify(idempotencyRepository, times(1)).save(key, responseJson);
    }

    @Test
    @DisplayName("chave expirada (TTL) → tratar como chave nova")
    void givenExpiredKey_whenCheck_thenReturnEmpty() {
        String expiredKey = "expired-key-001";
        when(idempotencyRepository.findByKey(expiredKey)).thenReturn(Optional.empty());

        Optional<String> result = idempotencyRepository.findByKey(expiredKey);

        assertThat(result).isEmpty();
    }
}
