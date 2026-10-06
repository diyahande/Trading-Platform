-- Migration 007: Add executor settlement columns to orders table
-- Executed after 006_transactions.sql
-- Adds columns for execution settlement and creates index for status filtering

ALTER TABLE orders ADD COLUMN executed_price NUMERIC(20, 4);
ALTER TABLE orders ADD COLUMN executed_on TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN rejection_reason VARCHAR(255);

-- Index for fast WHERE status='NEW' scan during execution
CREATE INDEX idx_orders_status_new ON orders(status) WHERE status = 'NEW';

-- Index for account_id + status scan during account-based checks
CREATE INDEX idx_orders_account_status ON orders(account_id, status);
