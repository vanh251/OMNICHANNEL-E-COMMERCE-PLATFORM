-- Apply to the Payment-owned DB, outside a transaction.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_outbox_pending_id ON outbox_events (published, id);
