package com.personal.transfer.application.ports.out;

import com.personal.transfer.domain.entities.Account;

import java.util.List;
import java.util.Optional;

public interface AccountPort {

    Optional<Account> findById(String accountId);

    List<Account> findAllByIds(List<String> accountIds);

    List<Account> findAllByIdsWithLock(List<String> accountIds);

    List<Account> saveAll(List<Account> accounts);
}
