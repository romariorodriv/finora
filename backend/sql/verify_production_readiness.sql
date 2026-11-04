-- Read-only PostgreSQL checks after V2_5, against the intended schema.
SELECT table_name, column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND ((table_name = 'planning_profiles' AND column_name IN ('balance_confirmed_at', 'version'))
    OR (table_name = 'planning_payments' AND column_name IN ('paid_at', 'version')))
ORDER BY table_name, column_name;
-- Expected: 4 columns; timestamps nullable, versions bigint NOT NULL default 0.
SELECT indexname, indexdef FROM pg_indexes
WHERE schemaname = current_schema() AND indexname = 'idx_transactions_user_date_id';
-- Legacy confirmations remain NULL until explicit confirmation by the user.
SELECT count(*) AS balances_requiring_confirmation FROM planning_profiles WHERE balance_confirmed_at IS NULL;
SELECT count(*) AS paid_payments FROM planning_payments WHERE paid_at IS NOT NULL;
