-- Deliberately has no foreign keys: an audit record must stay valid even if what it refers to changes or goes away.
CREATE TABLE audit_log (
    id            UUID         PRIMARY KEY,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    actor_user_id UUID,
    action        VARCHAR(40)  NOT NULL,
    target_type   VARCHAR(20),
    target_id     UUID,
    reason        VARCHAR(500),
    detail        VARCHAR(1000),
    CONSTRAINT ck_audit_log_target_pair CHECK ((target_type IS NULL) = (target_id IS NULL))
);

CREATE INDEX idx_audit_log_occurred ON audit_log (occurred_at DESC, id DESC);
CREATE INDEX idx_audit_log_actor ON audit_log (actor_user_id, occurred_at DESC);
CREATE INDEX idx_audit_log_target ON audit_log (target_id, occurred_at DESC);

-- Append-only, like the ledger: any attempt to change or remove a row is rejected by the database itself.
CREATE FUNCTION forbid_audit_log_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION forbid_audit_log_modification();