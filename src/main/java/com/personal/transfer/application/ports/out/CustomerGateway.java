package com.personal.transfer.application.ports.out;

import com.personal.transfer.application.dto.CustomerInfo;

public interface CustomerGateway {

    CustomerInfo fetchCustomer(String customerId);
}
