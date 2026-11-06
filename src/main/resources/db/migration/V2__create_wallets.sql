CREATE TABLE wallets (
    id         UUID          PRIMARY KEY,
    user_id    UUID          NOT NULL REFERENCES users (id),
    currency   VARCHAR(3)    NOT NULL,
    balance    NUMERIC(19,4) NOT NULL DEFAULT 0,
    status     VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    version    BIGINT        NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_wallets_user_currency UNIQUE (user_id, currency),
    CONSTRAINT ck_wallets_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT ck_wallets_status CHECK (status IN ('ACTIVE', 'FROZEN')),
    CONSTRAINT ck_wallets_currency CHECK (currency IN ('UGX', 'USD', 'EUR', 'KES'))
);