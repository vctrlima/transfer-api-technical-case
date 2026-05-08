package com.personal.transfer.infrastructure.adapters;

import com.personal.transfer.domain.exceptions.ExternalServiceException;
import com.personal.transfer.infrastructure.adapters.dto.BacenNotifyRequest;
import com.personal.transfer.infrastructure.adapters.feign.BacenFeignClient;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BacenApiAdapter implements BacenApiPort {

    private final BacenFeignClient feignClient;

    @Override
    @CircuitBreaker(name = "bacen", fallbackMethod = "notifyFallback")
    @Retry(name = "bacen")
    public void notify(BacenNotifyRequest request) {
        log.info("Notifying BACEN for transferId={}", request.transferId());
        try {
            feignClient.notify(request);
            log.info("BACEN notified successfully for transferId={}", request.transferId());
        } catch (FeignException.TooManyRequests e) {
            log.warn("BACEN rate limit (429) for transferId={} — message will be requeued via SQS", request.transferId());
            throw new BacenRateLimitException("BACEN rate limit exceeded for transferId=" + request.transferId());
        } catch (FeignException e) {
            log.error("BACEN API error for transferId={}: {}", request.transferId(), e.getMessage());
            throw new ExternalServiceException("BACEN API error: " + e.getMessage(), e);
        }
    }

    public void notifyFallback(BacenNotifyRequest request, Throwable t) {
        if (t instanceof BacenRateLimitException e) {
            throw e;
        }
        log.error("BACEN Circuit Breaker OPEN for transferId={}, error={}", request.transferId(), t.getMessage());
        throw new ExternalServiceException("BACEN service is unavailable (circuit breaker open): " + t.getMessage(), t);
    }
}
