package com.personal.transfer.infrastructure.adapters.feign;

import com.personal.transfer.infrastructure.adapters.dto.BacenNotifyRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "bacen", url = "${bacen.api.url}")
public interface BacenFeignClient {

    @PostMapping("/notify")
    void notify(@RequestBody BacenNotifyRequest request);
}
