-- Execute only after review, on the intended database. Not applied by this change.
BEGIN;
-- Keep legacy confirmations unknown; do not invent historical timestamps.
ALTER TABLE planning_profiles ADD COLUMN IF NOT EXISTS balance_confirmed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE planning_profiles ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE planning_payments ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE planning_payments ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_transactions_user_date_id ON transactions (user_id, date DESC, id DESC);
COMMIT;
