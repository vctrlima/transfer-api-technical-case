package com.personal.transfer.infrastructure.adapters;

import com.personal.transfer.infrastructure.adapters.dto.BacenNotifyRequest;

public interface BacenApiPort {

    void notify(BacenNotifyRequest request);
}
