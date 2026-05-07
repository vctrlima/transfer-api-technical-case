CREATE TABLE IF NOT EXISTS accounts (
    id VARCHAR(36) PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    balance DECIMAL(19, 2) NOT NULL,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS transfers (
    id VARCHAR(36) PRIMARY KEY,
    origin_account_id VARCHAR(36) NOT NULL,
    destination_account_id VARCHAR(36) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_transfers_idempotency_key ON transfers (idempotency_key);
CREATE INDEX IF NOT EXISTS idx_transfers_origin ON transfers (origin_account_id);
