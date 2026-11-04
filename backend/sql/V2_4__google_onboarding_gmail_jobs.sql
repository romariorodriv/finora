-- Sarela Google sign-in and persistent Gmail import. MANUAL, additive PostgreSQL migration.
-- Apply to a backed-up staging copy first. Does not reseed categories or assign ADMIN.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout='5s';
DO $$ BEGIN
 IF to_regclass('public.app_users') IS NULL OR to_regclass('public.transactions') IS NULL
 OR to_regclass('public.gmail_connections') IS NULL OR to_regclass('public.imported_messages') IS NULL
 OR to_regclass('public.gmail_oauth_states') IS NULL OR to_regclass('public.managed_categories') IS NULL
 OR to_regclass('public.classification_rules') IS NULL OR to_regclass('public.classification_batches') IS NULL
 OR to_regclass('public.admin_audit') IS NULL
 THEN RAISE EXCEPTION 'Missing existing Sarela tables. Review the base schema and V2_2/V2_3 before applying V2_4; do not run old category seeds blindly.'; END IF;
END $$;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS role VARCHAR(16) DEFAULT 'USER';
UPDATE public.app_users SET role='USER' WHERE role IS NULL;
ALTER TABLE public.app_users ALTER COLUMN role SET DEFAULT 'USER', ALTER COLUMN role SET NOT NULL;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.app_users'::regclass AND conname='app_users_role_check')
 THEN ALTER TABLE public.app_users ADD CONSTRAINT app_users_role_check CHECK(role IN ('USER','ADMIN')); END IF;
END $$;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS google_subject VARCHAR(255);
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS google_linked_at TIMESTAMPTZ;
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_google_subject ON public.app_users(google_subject) WHERE google_subject IS NOT NULL;
-- Fail on ambiguous historical identities rather than linking the wrong account.
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_normalized_email ON public.app_users(lower(email));
-- Repair the partial Hibernate-created schema observed on the existing server.
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS classification_source VARCHAR(30) DEFAULT 'LEGACY';
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS currency VARCHAR(3) DEFAULT 'UNK';
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS date_source VARCHAR(30) DEFAULT 'LEGACY_UNKNOWN';
UPDATE public.transactions SET classification_source='LEGACY' WHERE classification_source IS NULL;
UPDATE public.transactions SET currency='UNK' WHERE currency IS NULL;
UPDATE public.transactions SET version=0 WHERE version IS NULL;
UPDATE public.transactions SET date_source='LEGACY_UNKNOWN' WHERE date_source IS NULL;
ALTER TABLE public.transactions
 ALTER COLUMN classification_source SET DEFAULT 'LEGACY', ALTER COLUMN classification_source SET NOT NULL,
 ALTER COLUMN currency SET DEFAULT 'UNK', ALTER COLUMN currency SET NOT NULL,
 ALTER COLUMN version SET DEFAULT 0, ALTER COLUMN version SET NOT NULL,
 ALTER COLUMN date_source SET DEFAULT 'USER_PROVIDED', ALTER COLUMN date_source SET NOT NULL;
UPDATE public.managed_categories SET version=0 WHERE version IS NULL;
UPDATE public.classification_rules SET version=0 WHERE version IS NULL;
UPDATE public.classification_batches SET version=0 WHERE version IS NULL;
ALTER TABLE public.managed_categories ALTER COLUMN version SET DEFAULT 0, ALTER COLUMN version SET NOT NULL;
ALTER TABLE public.classification_rules ALTER COLUMN version SET DEFAULT 0, ALTER COLUMN version SET NOT NULL;
ALTER TABLE public.classification_batches ALTER COLUMN version SET DEFAULT 0, ALTER COLUMN version SET NOT NULL;
ALTER TABLE public.admin_audit ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE public.gmail_oauth_states ADD COLUMN IF NOT EXISTS browser_hash VARCHAR(64);
ALTER TABLE public.gmail_oauth_states ADD COLUMN IF NOT EXISTS encrypted_verifier VARCHAR(4096);
-- Old, short-lived states have no browser proof and will be refused; start consent again.
CREATE TABLE IF NOT EXISTS public.google_auth_flows(
 state_hash VARCHAR(64) PRIMARY KEY, browser_hash VARCHAR(64) NOT NULL,
 nonce_hash VARCHAR(64) NOT NULL, encrypted_verifier VARCHAR(4096) NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL, consumed_at TIMESTAMPTZ, completed_at TIMESTAMPTZ,
 link_user_id BIGINT REFERENCES public.app_users(id) ON DELETE CASCADE,
 google_subject VARCHAR(255),google_email VARCHAR(255),google_name VARCHAR(255),
 link_attempts INTEGER NOT NULL DEFAULT 0 CHECK(link_attempts BETWEEN 0 AND 5),
 version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_google_auth_expiry ON public.google_auth_flows(expires_at);
CREATE TABLE IF NOT EXISTS public.account_sessions(
 token_hash VARCHAR(64) PRIMARY KEY,user_id BIGINT NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_account_sessions_expiry ON public.account_sessions(expires_at);
CREATE UNIQUE INDEX IF NOT EXISTS uq_gmail_connection_owner ON public.gmail_connections(id,user_id);
CREATE TABLE IF NOT EXISTS public.gmail_sync_jobs(
 user_id BIGINT PRIMARY KEY REFERENCES public.app_users(id) ON DELETE CASCADE,
 run_id VARCHAR(36) NOT NULL,connection_id BIGINT NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 period_from TIMESTAMPTZ NOT NULL,period_until TIMESTAMPTZ NOT NULL,
 next_page_token TEXT,queued_at TIMESTAMPTZ NOT NULL DEFAULT now(),started_at TIMESTAMPTZ,
 completed_at TIMESTAMPTZ,updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),next_attempt_at TIMESTAMPTZ,
 lease_until TIMESTAMPTZ,lease_token VARCHAR(36),error_code VARCHAR(80),
 retry_count INTEGER NOT NULL DEFAULT 0,pages_processed INTEGER NOT NULL DEFAULT 0,
 initial_import BOOLEAN NOT NULL DEFAULT TRUE,version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_gmail_job_mailbox_owner FOREIGN KEY(connection_id,user_id) REFERENCES public.gmail_connections(id,user_id) ON DELETE CASCADE,
 CONSTRAINT gmail_job_status_check CHECK(status IN ('PENDING','IMPORTING','COMPLETED','PARTIAL','ERROR','CANCELLED')),
 CHECK(period_until>=period_from)
);
CREATE INDEX IF NOT EXISTS idx_gmail_jobs_due ON public.gmail_sync_jobs(status,next_attempt_at,lease_until);
CREATE TABLE IF NOT EXISTS public.gmail_job_items(
 id BIGSERIAL PRIMARY KEY,run_id VARCHAR(36) NOT NULL,
 user_id BIGINT NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
 message_id VARCHAR(255) NOT NULL,outcome VARCHAR(20) NOT NULL,error_code VARCHAR(80),created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT uq_gmail_job_item UNIQUE(run_id,message_id),
 CHECK(outcome IN ('CREATED','DUPLICATE','REVIEW','UNSUPPORTED','REJECTED','FAILED'))
);
CREATE INDEX IF NOT EXISTS idx_gmail_job_items_owner_run ON public.gmail_job_items(user_id,run_id);
ALTER TABLE public.imported_messages ADD COLUMN IF NOT EXISTS processing_attempts INTEGER DEFAULT 0;
UPDATE public.imported_messages SET processing_attempts=0 WHERE processing_attempts IS NULL;
ALTER TABLE public.imported_messages ALTER COLUMN processing_attempts SET DEFAULT 0,ALTER COLUMN processing_attempts SET NOT NULL;
-- Message IDs are mailbox-local. Preserve old rows and replace the overly broad unique constraint.
DO $$ DECLARE c RECORD; BEGIN
 FOR c IN SELECT con.conname FROM pg_constraint con
 WHERE con.conrelid='public.imported_messages'::regclass AND con.contype='u'
 AND (SELECT array_agg(a.attname::text ORDER BY a.attname) FROM unnest(con.conkey) k JOIN pg_attribute a ON a.attrelid=con.conrelid AND a.attnum=k)
 =ARRAY['external_message_id','user_id']::text[]
 LOOP EXECUTE format('ALTER TABLE public.imported_messages DROP CONSTRAINT %I',c.conname); END LOOP;
END $$;
DO $$ DECLARE i RECORD; BEGIN
 FOR i IN SELECT ns.nspname,idx.relname FROM pg_index pi
 JOIN pg_class idx ON idx.oid=pi.indexrelid JOIN pg_namespace ns ON ns.oid=idx.relnamespace
 WHERE pi.indrelid='public.imported_messages'::regclass AND pi.indisunique AND NOT pi.indisprimary
 AND NOT EXISTS(SELECT 1 FROM pg_constraint c WHERE c.conindid=pi.indexrelid)
 AND (SELECT array_agg(a.attname::text ORDER BY a.attname) FROM unnest(pi.indkey::smallint[]) k JOIN pg_attribute a ON a.attrelid=pi.indrelid AND a.attnum=k)
 =ARRAY['external_message_id','user_id']::text[]
 LOOP EXECUTE format('DROP INDEX %I.%I',i.nspname,i.relname); END LOOP;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_imported_message_mailbox ON public.imported_messages(user_id,gmail_connection_id,external_message_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_transactions_owner_source ON public.transactions(user_id,external_id);
CREATE TABLE IF NOT EXISTS public.data_deletion_requests(
 user_id BIGINT PRIMARY KEY REFERENCES public.app_users(id) ON DELETE CASCADE,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','COMPLETED')),
 requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),completed_at TIMESTAMPTZ
);
-- Also cover tables that a previous ddl-auto=update attempt already created without FKs.
DO $$ DECLARE edge RECORD; key_number SMALLINT; BEGIN
 FOR edge IN SELECT * FROM (VALUES
  ('account_sessions','user_id','fk_sessions_user'),
  ('google_auth_flows','link_user_id','fk_google_flow_link_user'),
  ('gmail_sync_jobs','user_id','fk_gmail_jobs_user'),
  ('gmail_job_items','user_id','fk_gmail_job_items_user'),
  ('data_deletion_requests','user_id','fk_deletion_request_user')
 ) AS edges(table_name,column_name,constraint_name)
 LOOP
  SELECT attnum INTO key_number FROM pg_attribute WHERE attrelid=to_regclass('public.'||edge.table_name) AND attname=edge.column_name;
  IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid=to_regclass('public.'||edge.table_name) AND contype='f' AND confrelid='public.app_users'::regclass AND conkey=ARRAY[key_number]::smallint[])
  THEN EXECUTE format('ALTER TABLE public.%I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES public.app_users(id) ON DELETE CASCADE',edge.table_name,edge.constraint_name,edge.column_name); END IF;
 END LOOP;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.gmail_sync_jobs'::regclass AND conname='fk_gmail_job_mailbox_owner')
 THEN ALTER TABLE public.gmail_sync_jobs ADD CONSTRAINT fk_gmail_job_mailbox_owner FOREIGN KEY(connection_id,user_id) REFERENCES public.gmail_connections(id,user_id) ON DELETE CASCADE; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.gmail_sync_jobs'::regclass AND conname='gmail_job_status_check')
 THEN ALTER TABLE public.gmail_sync_jobs ADD CONSTRAINT gmail_job_status_check CHECK(status IN ('PENDING','IMPORTING','COMPLETED','PARTIAL','ERROR','CANCELLED')); END IF;
END $$;
COMMIT;
