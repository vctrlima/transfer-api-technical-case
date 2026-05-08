package com.personal.transfer.infrastructure.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.transfer.domain.exceptions.ExternalServiceException;
import com.personal.transfer.infrastructure.adapters.dto.CustomerResponse;
import com.personal.transfer.infrastructure.adapters.feign.CadastroFeignClient;
import com.personal.transfer.infrastructure.redis.CustomerCacheRepository;
import com.personal.transfer.infrastructure.sqs.BacenEventConsumer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
@DisplayName("CadastroApiAdapter — cache, validação e circuit breaker")
class CadastroApiAdapterTest {

    @MockBean
    CadastroFeignClient cadastroFeignClient;

    @MockBean
    CustomerCacheRepository customerCacheRepository;

    @MockBean
    RedisConnectionFactory redisConnectionFactory;

    @MockBean
    SqsClient sqsClient;

    @MockBean
    BacenEventConsumer bacenEventConsumer;

    @Autowired
    CadastroApiAdapter cadastroApiAdapter;

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        resetCircuitBreaker();
    }

    private void resetCircuitBreaker() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("cadastro");
        if (cb.getState() == CircuitBreaker.State.CLOSED) return;
        cb.transitionToDisabledState();
        cb.transitionToClosedState();
    }

    @Nested
    @Order(1)
    @DisplayName("Busca via API")
    class FetchFromApi {

        @Test
        @DisplayName("resposta válida da API → retorna CustomerResponse e armazena no cache")
        void givenValidApiResponse_whenFetchCustomer_thenReturnsCustomerAndCachesIt() throws Exception {
            var customer = new CustomerResponse("acc-001", "Victor Lima", "ACTIVE");
            when(customerCacheRepository.get("acc-001")).thenReturn(Optional.empty());
            when(cadastroFeignClient.getCustomer("acc-001")).thenReturn(customer);

            CustomerResponse result = cadastroApiAdapter.fetchCustomer("acc-001");

            assertThat(result).isEqualTo(customer);
            verify(customerCacheRepository).put(eq("acc-001"), anyString());
        }

        @Test
        @DisplayName("resposta da API sem 'name' → lança ExternalServiceException")
        void givenApiResponseWithNullName_whenFetchCustomer_thenThrowsExternalServiceException() {
            var invalidCustomer = new CustomerResponse("acc-001", null, "ACTIVE");
            when(customerCacheRepository.get("acc-001")).thenReturn(Optional.empty());
            when(cadastroFeignClient.getCustomer("acc-001")).thenReturn(invalidCustomer);

            assertThatThrownBy(() -> cadastroApiAdapter.fetchCustomer("acc-001"))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("missing name field");
        }

        @Test
        @DisplayName("API retorna null → lança ExternalServiceException")
        void givenNullApiResponse_whenFetchCustomer_thenThrowsExternalServiceException() {
            when(customerCacheRepository.get("acc-001")).thenReturn(Optional.empty());
            when(cadastroFeignClient.getCustomer("acc-001")).thenReturn(null);

            assertThatThrownBy(() -> cadastroApiAdapter.fetchCustomer("acc-001"))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("missing name field");
        }
    }

    @Nested
    @Order(2)
    @DisplayName("Cache")
    class CacheBehavior {

        @Test
        @DisplayName("cache hit com JSON válido → retorna do cache sem chamar a API")
        void givenCachedCustomer_whenFetchCustomer_thenReturnsCachedValueWithoutCallingApi() throws Exception {
            var customer = new CustomerResponse("acc-001", "Victor Lima", "ACTIVE");
            String json = objectMapper.writeValueAsString(customer);
            when(customerCacheRepository.get("acc-001")).thenReturn(Optional.of(json));

            CustomerResponse result = cadastroApiAdapter.fetchCustomer("acc-001");

            assertThat(result).isEqualTo(customer);
            verifyNoInteractions(cadastroFeignClient);
        }

        @Test
        @DisplayName("cache hit com JSON inválido → ignora cache e busca da API")
        void givenInvalidCachedJson_whenFetchCustomer_thenFallsBackToApi() {
            var customer = new CustomerResponse("acc-001", "Victor Lima", "ACTIVE");
            when(customerCacheRepository.get("acc-001")).thenReturn(Optional.of("not-valid-json"));
            when(cadastroFeignClient.getCustomer("acc-001")).thenReturn(customer);

            CustomerResponse result = cadastroApiAdapter.fetchCustomer("acc-001");

            assertThat(result).isEqualTo(customer);
            verify(cadastroFeignClient).getCustomer("acc-001");
        }

        @Test
        @DisplayName("cache miss → chama a API")
        void givenCacheMiss_whenFetchCustomer_thenCallsApi() {
            var customer = new CustomerResponse("acc-001", "Victor Lima", "ACTIVE");
            when(customerCacheRepository.get("acc-001")).thenReturn(Optional.empty());
            when(cadastroFeignClient.getCustomer("acc-001")).thenReturn(customer);

            CustomerResponse result = cadastroApiAdapter.fetchCustomer("acc-001");

            assertThat(result).isEqualTo(customer);
            verify(cadastroFeignClient).getCustomer("acc-001");
        }
    }

    @Nested
    @Order(3)
    @DisplayName("Circuit Breaker")
    class CircuitBreakerBehavior {

        @Test
        @DisplayName("Circuit Breaker abre após falhas repetidas e fallback lança ExternalServiceException")
        void givenRepeatedApiFailures_whenFetchCustomer_thenCircuitBreakerOpensAndFallbackActivates() {
            when(customerCacheRepository.get(anyString())).thenReturn(Optional.empty());
            when(cadastroFeignClient.getCustomer(anyString())).thenThrow(new RuntimeException("503 Service Unavailable"));

            for (int i = 0; i < 3; i++) {
                try {
                    cadastroApiAdapter.fetchCustomer("acc-001");
                } catch (Exception ignored) {
                }
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("cadastro");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            assertThatThrownBy(() -> cadastroApiAdapter.fetchCustomer("acc-001"))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("unavailable");
        }
    }
}