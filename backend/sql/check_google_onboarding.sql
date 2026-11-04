-- Read-only checks before starting V2_4 backend. Must return PASS.
\set ON_ERROR_STOP on
DO $$ DECLARE name TEXT; col RECORD; BEGIN
 FOREACH name IN ARRAY ARRAY['app_users','transactions','managed_categories','classification_rules','classification_batches','admin_audit','gmail_connections','gmail_oauth_states','imported_messages','google_auth_flows','account_sessions','gmail_sync_jobs','gmail_job_items','data_deletion_requests']
 LOOP IF to_regclass('public.'||name) IS NULL THEN RAISE EXCEPTION 'Missing table: %',name; END IF; END LOOP;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='app_users' AND column_name='role' AND is_nullable='NO') THEN RAISE EXCEPTION 'Missing required role column'; END IF;
 FOR col IN SELECT * FROM (VALUES
  ('app_users','google_subject'),('app_users','google_linked_at'),('transactions','classification_source'),
  ('transactions','currency'),('transactions','version'),('transactions','date_source'),
  ('gmail_oauth_states','browser_hash'),('gmail_oauth_states','encrypted_verifier'),
  ('imported_messages','processing_attempts'),('google_auth_flows','nonce_hash'),
  ('google_auth_flows','encrypted_verifier'),('google_auth_flows','browser_hash'),
  ('google_auth_flows','link_attempts'),('account_sessions','token_hash'),
  ('account_sessions','expires_at'),('gmail_sync_jobs','next_page_token'),
  ('gmail_sync_jobs','lease_token'),('gmail_sync_jobs','period_from'),
  ('gmail_sync_jobs','period_until'),('gmail_job_items','outcome'),
  ('data_deletion_requests','requested_at')
 ) AS expected(table_name,column_name)
 LOOP
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name=col.table_name AND c.column_name=col.column_name)
  THEN RAISE EXCEPTION 'Missing V2_4 column: %.%',col.table_name,col.column_name; END IF;
 END LOOP;
 IF EXISTS(SELECT 1 FROM public.transactions WHERE version IS NULL OR currency IS NULL OR classification_source IS NULL OR date_source IS NULL) THEN RAISE EXCEPTION 'Legacy metadata must be backfilled'; END IF;
 IF EXISTS(SELECT 1 FROM public.app_users WHERE role IS NULL OR role NOT IN ('USER','ADMIN')) THEN RAISE EXCEPTION 'Invalid role metadata'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname='uq_imported_message_mailbox') THEN RAISE EXCEPTION 'Missing mailbox-local deduplication index'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.gmail_sync_jobs'::regclass AND conname='fk_gmail_job_mailbox_owner') THEN RAISE EXCEPTION 'Missing mailbox ownership FK'; END IF;
END $$;
SELECT 'PASS: V2_4 tables, roles, legacy metadata, ownership and deduplication are present; also run Hibernate ddl-auto=validate before serving traffic.' AS result;
SELECT count(*) AS unlinked_legacy_accounts FROM public.app_users WHERE google_subject IS NULL;
SELECT status,count(*) FROM public.gmail_sync_jobs GROUP BY status ORDER BY status;
