package com.personal.transfer.infrastructure.adapters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.transfer.domain.exceptions.ExternalServiceException;
import com.personal.transfer.infrastructure.adapters.dto.CustomerResponse;
import com.personal.transfer.infrastructure.adapters.feign.CadastroFeignClient;
import com.personal.transfer.infrastructure.redis.CustomerCacheRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CadastroApiAdapter implements CadastroApiPort {

    private final CadastroFeignClient feignClient;
    private final CustomerCacheRepository customerCacheRepository;
    private final ObjectMapper objectMapper;

    @Override
    @CircuitBreaker(name = "cadastro", fallbackMethod = "fetchCustomerFallback")
    @Retry(name = "cadastro")
    public CustomerResponse fetchCustomer(String customerId) {
        var cached = customerCacheRepository.get(customerId);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), CustomerResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached customer for id={}, fetching from API", customerId);
            }
        }

        log.info("Fetching customer data for accountId={}", customerId);
        CustomerResponse response = feignClient.getCustomer(customerId);
        if (response == null || response.name() == null) {
            throw new ExternalServiceException("Invalid response from Cadastro API: missing name field");
        }

        try {
            customerCacheRepository.put(customerId, objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize customer response for caching, accountId={}", customerId);
        }

        return response;
    }

    public CustomerResponse fetchCustomerFallback(String customerId, Throwable t) {
        log.error("Circuit breaker activated for Cadastro API, customerId={}, error={}", customerId, t.getMessage());
        throw new ExternalServiceException("Cadastro API is unavailable: " + t.getMessage(), t);
    }
}
