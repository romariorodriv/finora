-- Apply manually to the SAME PostgreSQL database used by production BEFORE replacing the JAR.
-- Additive schema only. Keep independent PostgreSQL backup before deployment.
BEGIN;
CREATE TABLE IF NOT EXISTS planning_profiles (
 id BIGSERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL UNIQUE REFERENCES app_users(id) ON DELETE CASCADE,
 confirmed_balance NUMERIC(16,2) NOT NULL,
 reserve_amount NUMERIC(16,2) NOT NULL,
 next_income_date DATE NOT NULL,
 CONSTRAINT planning_profile_balance_nonnegative CHECK (confirmed_balance >= 0 AND reserve_amount >= 0)
);
CREATE TABLE IF NOT EXISTS planning_payments (
 id BIGSERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
 name VARCHAR(100) NOT NULL,
 due_date DATE NOT NULL,
 amount NUMERIC(16,2) NOT NULL CHECK (amount > 0)
);
CREATE INDEX IF NOT EXISTS idx_planning_payments_user ON planning_payments(user_id);
COMMIT;
