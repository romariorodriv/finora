-- SAFE-DEPLOY PRECONDITION, NOT AUTO-EXECUTED.
-- Apply to a restored/staging PostgreSQL copy first; take a verified production backup.
-- Ensures concurrent writes cannot recreate orphan personal records after account removal.
-- Existing orphan records cause an error (fail closed); do NOT silently delete them.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout='5s';
DO $$
DECLARE edge RECORD; col_num SMALLINT;
BEGIN
 FOR edge IN SELECT * FROM (VALUES
   ('transactions','user_id','fk_transactions_user_deletion'),
   ('gmail_connections','user_id','fk_gmail_connections_user_deletion'),
   ('gmail_oauth_states','user_id','fk_gmail_oauth_states_user_deletion'),
   ('imported_messages','user_id','fk_imported_messages_user_deletion'),
   ('user_feedback','user_id','fk_user_feedback_user_deletion'),
   ('planning_profiles','user_id','fk_planning_profiles_user_deletion'),
   ('planning_payments','user_id','fk_planning_payments_user_deletion'),
   ('classification_rules','user_id','fk_classification_rules_user_deletion')
 ) AS x(table_name,column_name,constraint_name)
 LOOP
   IF to_regclass('public.'||edge.table_name) IS NULL
      THEN RAISE EXCEPTION 'Missing table for account deletion: %',edge.table_name; END IF;
   SELECT a.attnum INTO col_num FROM pg_attribute a
     WHERE a.attrelid=to_regclass('public.'||edge.table_name) AND a.attname=edge.column_name;
   IF col_num IS NULL THEN RAISE EXCEPTION 'Missing ownership column: %.%',edge.table_name,edge.column_name; END IF;
   IF NOT EXISTS(
      SELECT 1 FROM pg_constraint c WHERE c.contype='f'
       AND c.conrelid=to_regclass('public.'||edge.table_name)
       AND c.confrelid='public.app_users'::regclass
       AND c.conkey=ARRAY[col_num]::smallint[]
   ) THEN
      EXECUTE format('ALTER TABLE public.%I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES public.app_users(id) ON DELETE CASCADE',
         edge.table_name,edge.constraint_name,edge.column_name);
   END IF;
 END LOOP;
END $$;
COMMIT;

-- Read-only post-check:
-- SELECT conrelid::regclass, conname, pg_get_constraintdef(oid)
-- FROM pg_constraint WHERE contype='f' AND confrelid='public.app_users'::regclass;
