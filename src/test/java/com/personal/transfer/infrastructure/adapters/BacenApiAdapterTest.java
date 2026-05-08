package com.personal.transfer.infrastructure.adapters;

import com.personal.transfer.domain.exceptions.ExternalServiceException;
import com.personal.transfer.infrastructure.adapters.dto.BacenNotifyRequest;
import com.personal.transfer.infrastructure.adapters.feign.BacenFeignClient;
import com.personal.transfer.infrastructure.sqs.BacenEventConsumer;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "management.health.redis.enabled=false",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration," +
                        "org.springframework.boot.actuate.autoconfigure.data.redis.RedisReactiveHealthContributorAutoConfiguration," +
                        "org.springframework.boot.actuate.autoconfigure.data.redis.RedisHealthContributorAutoConfiguration"
        }
)
@ActiveProfiles("test")
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@DisplayName("BacenApiAdapter — Circuit Breaker e tratamento de erros")
class BacenApiAdapterTest {

    @MockBean
    BacenFeignClient bacenFeignClient;

    @MockBean
    RedisConnectionFactory redisConnectionFactory;

    @MockBean
    SqsClient sqsClient;

    @MockBean
    BacenEventConsumer bacenEventConsumer;

    @Autowired
    BacenApiAdapter bacenApiAdapter;

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    private BacenNotifyRequest request;

    @BeforeEach
    void setUp() {
        request = new BacenNotifyRequest(
                "t-001", "acc-origin-001", "acc-dest-001",
                new BigDecimal("200.00"), LocalDateTime.now()
        );
        resetCircuitBreaker();
    }

    /**
     * Forces CB back to CLOSED from any state.
     * OPEN/HALF_OPEN → DISABLED → CLOSED is always a valid path in Resilience4j.
     */
    private void resetCircuitBreaker() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("bacen");
        if (cb.getState() == CircuitBreaker.State.CLOSED) return;
        cb.transitionToDisabledState();
        cb.transitionToClosedState();
    }

    @Nested
    @Order(1)
    @DisplayName("Tratamento de erros HTTP")
    class ErrorHandling {

        @Test
        @DisplayName("sucesso → sem exceção")
        void givenSuccess_whenNotify_thenNoException() {
            doNothing().when(bacenFeignClient).notify(any());

            assertThatCode(() -> bacenApiAdapter.notify(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("429 Too Many Requests → lançar BacenRateLimitException (não retentado pelo @Retry)")
        void given429Response_whenNotify_thenThrowsBacenRateLimitException() {
            FeignException.TooManyRequests tooMany = mock(FeignException.TooManyRequests.class);
            when(tooMany.getMessage()).thenReturn("429 Too Many Requests");
            doThrow(tooMany).when(bacenFeignClient).notify(any());

            assertThatThrownBy(() -> bacenApiAdapter.notify(request))
                    .isInstanceOf(BacenRateLimitException.class)
                    .hasMessageContaining("t-001");
        }

        @Test
        @DisplayName("5xx Server Error → lançar ExternalServiceException")
        void givenServerError_whenNotify_thenThrowsExternalServiceException() {
            FeignException serverError = mock(FeignException.class);
            when(serverError.getMessage()).thenReturn("500 Internal Server Error");
            doThrow(serverError).when(bacenFeignClient).notify(any());

            assertThatThrownBy(() -> bacenApiAdapter.notify(request))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("BACEN API error");
        }
    }

    @Nested
    @Order(2)
    @DisplayName("Circuit Breaker")
    class CircuitBreakerBehavior {

        @Test
        @DisplayName("Circuit Breaker abre após 3 falhas e bloqueia chamadas seguintes com fallback")
        void givenRepeatedServerErrors_whenNotify_thenCircuitBreakerOpensAndFallbackActivates() {
            FeignException serverError = mock(FeignException.class);
            when(serverError.getMessage()).thenReturn("500 Internal Server Error");
            doThrow(serverError).when(bacenFeignClient).notify(any());

            for (int i = 0; i < 3; i++) {
                try {
                    bacenApiAdapter.notify(request);
                } catch (Exception ignored) {
                }
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("bacen");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            assertThatThrownBy(() -> bacenApiAdapter.notify(request))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("unavailable");
        }
    }
}

