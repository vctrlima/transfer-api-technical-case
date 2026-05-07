package com.personal.transfer.infrastructure.adapters;

import com.personal.transfer.infrastructure.adapters.dto.CustomerResponse;

public interface CadastroApiPort {

    CustomerResponse fetchCustomer(String customerId);
}
