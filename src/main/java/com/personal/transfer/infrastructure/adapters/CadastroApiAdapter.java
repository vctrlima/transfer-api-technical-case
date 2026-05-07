package com.personal.transfer.infrastructure.adapters;

import com.personal.transfer.domain.exceptions.ExternalServiceException;
import com.personal.transfer.infrastructure.adapters.dto.CustomerResponse;
import com.personal.transfer.infrastructure.adapters.feign.CadastroFeignClient;
import feign.FeignException;
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

    @Override
    @CircuitBreaker(name = "cadastro", fallbackMethod = "fetchCustomerFallback")
    @Retry(name = "cadastro")
    public CustomerResponse fetchCustomer(String customerId) {
        log.info("Fetching customer data for accountId={}", customerId);
        CustomerResponse response = feignClient.getCustomer(customerId);
        if (response == null || response.name() == null) {
            throw new ExternalServiceException("Invalid response from Cadastro API: missing name field");
        }
        return response;
    }

    public CustomerResponse fetchCustomerFallback(String customerId, Throwable t) {
        log.error("Circuit breaker activated for Cadastro API, customerId={}, error={}", customerId, t.getMessage());
        throw new ExternalServiceException("Cadastro API is unavailable: " + t.getMessage(), t);
    }
}
