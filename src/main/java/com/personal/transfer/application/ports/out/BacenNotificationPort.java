package com.personal.transfer.application.ports.out;

import com.personal.transfer.application.dto.BacenNotification;

public interface BacenNotificationPort {

    void notify(BacenNotification notification);
}
