CREATE TABLE transactions (
    id                    UUID          PRIMARY KEY,
    reference             VARCHAR(20)   NOT NULL,
    user_id               UUID          NOT NULL REFERENCES users (id),
    source_wallet_id      UUID          REFERENCES wallets (id),
    destination_wallet_id UUID          REFERENCES wallets (id),
    type                  VARCHAR(20)   NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    amount                NUMERIC(19,4) NOT NULL,
    currency              VARCHAR(3)    NOT NULL,
    idempotency_key       VARCHAR(100)  NOT NULL,
    request_hash          VARCHAR(64)   NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_transactions_reference UNIQUE (reference),
    CONSTRAINT uq_transactions_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transactions_type CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT ck_transactions_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_transactions_wallets_match_type CHECK (
        (type = 'DEPOSIT'    AND source_wallet_id IS NULL     AND destination_wallet_id IS NOT NULL)
     OR (type = 'WITHDRAWAL' AND source_wallet_id IS NOT NULL AND destination_wallet_id IS NULL)
     OR (type = 'TRANSFER'   AND source_wallet_id IS NOT NULL AND destination_wallet_id IS NOT NULL
                             AND source_wallet_id <> destination_wallet_id)
    )
);

CREATE TABLE ledger_entries (
    id             UUID          PRIMARY KEY,
    transaction_id UUID          NOT NULL REFERENCES transactions (id),
    wallet_id      UUID          NOT NULL REFERENCES wallets (id),
    direction      VARCHAR(6)    NOT NULL,
    amount         NUMERIC(19,4) NOT NULL,
    balance_after  NUMERIC(19,4) NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_ledger_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_ledger_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_ledger_balance_after_non_negative CHECK (balance_after >= 0)
);

CREATE INDEX idx_ledger_entries_wallet_created ON ledger_entries (wallet_id, created_at);
CREATE INDEX idx_ledger_entries_transaction ON ledger_entries (transaction_id);

-- The ledger is append-only: any attempt to change or remove a row is rejected by the database itself.
CREATE FUNCTION forbid_ledger_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entries_immutable
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_modification();