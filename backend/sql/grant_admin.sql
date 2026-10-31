-- Run only by an authorized database operator AFTER V2_3, using an existing account.
-- psql -v ON_ERROR_STOP=1 -v admin_email='your-existing-email' -f grant_admin.sql
\set ON_ERROR_STOP on
\if :{?admin_email}
BEGIN;
CREATE TEMP TABLE sarela_admin_target ON COMMIT DROP AS SELECT id,role FROM app_users WHERE lower(email)=lower(:'admin_email');
DO $$ BEGIN IF (SELECT count(*) FROM sarela_admin_target)<>1 THEN RAISE EXCEPTION 'Expected exactly one existing account'; END IF; END $$;
SELECT u.id FROM app_users u JOIN sarela_admin_target t ON t.id=u.id FOR UPDATE;
UPDATE app_users u SET role='ADMIN' FROM sarela_admin_target t WHERE u.id=t.id;
INSERT INTO admin_audit(actor_id,actor_kind,action,entity_type,entity_id,before_json,after_json)
 SELECT NULL,'DATABASE_OPERATOR','ROLE_GRANT','USER',id::text,json_build_object('role',role)::text,json_build_object('role','ADMIN','operator',current_user)::text FROM sarela_admin_target;
COMMIT;
\else
\echo Missing required psql variable admin_email. No changes made.
\quit 1
\endif
