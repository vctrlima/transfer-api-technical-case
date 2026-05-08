package com.personal.transfer.application.ports.out;

import com.personal.transfer.application.dto.BacenTransferEvent;

public interface BacenEventPublisherPort {

    void publish(BacenTransferEvent event);
}
