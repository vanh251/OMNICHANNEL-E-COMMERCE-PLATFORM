-- Apply to the Order-owned DB before deploying to an existing busy environment.
-- Run outside a transaction; CONCURRENTLY allows ongoing writes.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_outbox_pending_id ON outbox_events (published, id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reservation_order_id ON inventory_reservation (order_id);
