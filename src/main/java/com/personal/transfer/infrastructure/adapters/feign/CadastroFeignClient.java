package com.personal.transfer.infrastructure.adapters.feign;

import com.personal.transfer.infrastructure.adapters.dto.CustomerResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "cadastro", url = "${cadastro.api.url}")
public interface CadastroFeignClient {

    @GetMapping("/customers/{id}")
    CustomerResponse getCustomer(@PathVariable("id") String customerId);
}
