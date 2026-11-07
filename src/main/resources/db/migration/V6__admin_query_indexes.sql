-- Admin lists show newest first, so these indexes store rows in exactly that order.
CREATE INDEX idx_transactions_created_id ON transactions (created_at DESC, id DESC);
CREATE INDEX idx_users_created_id ON users (created_at DESC, id DESC);
CREATE INDEX idx_wallets_created_id ON wallets (created_at DESC, id DESC);

-- Foreign keys do not get an index automatically in PostgreSQL. "Transactions touching this wallet" needs these.
CREATE INDEX idx_transactions_source_wallet ON transactions (source_wallet_id) WHERE source_wallet_id IS NOT NULL;
CREATE INDEX idx_transactions_destination_wallet ON transactions (destination_wallet_id) WHERE destination_wallet_id IS NOT NULL;