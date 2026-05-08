package com.personal.transfer.infrastructure.persistence.mappers;

import com.personal.transfer.domain.entities.Account;
import com.personal.transfer.infrastructure.persistence.entities.AccountJpaEntity;

public final class AccountPersistenceMapper {

    private AccountPersistenceMapper() {
    }

    public static Account toDomain(AccountJpaEntity entity) {
        return Account.builder()
                .id(entity.getId())
                .status(entity.getStatus())
                .balance(entity.getBalance())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static AccountJpaEntity toEntity(Account account) {
        return AccountJpaEntity.builder()
                .id(account.getId())
                .status(account.getStatus())
                .balance(account.getBalance())
                .version(account.getVersion())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
