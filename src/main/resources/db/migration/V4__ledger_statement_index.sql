-- A statement reads one wallet's entries newest first and breaks ties by id.
-- This index stores them in exactly that order, so a page can be read without sorting the whole history.
CREATE INDEX idx_ledger_entries_wallet_created_id ON ledger_entries (wallet_id, created_at DESC, id DESC);

-- The V3 index is now redundant: the new one also serves every query it did.
DROP INDEX idx_ledger_entries_wallet_created;