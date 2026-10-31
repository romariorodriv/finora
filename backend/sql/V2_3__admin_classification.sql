-- Additive PostgreSQL migration. Apply manually after backup, never automatically against production.
BEGIN;
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS role VARCHAR(16) NOT NULL DEFAULT 'USER';
DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='app_users_role_check') THEN ALTER TABLE app_users ADD CONSTRAINT app_users_role_check CHECK(role IN ('USER','ADMIN')); END IF; END $$;
CREATE TABLE IF NOT EXISTS managed_categories (
 id BIGSERIAL PRIMARY KEY,name VARCHAR(100) NOT NULL,name_key VARCHAR(120) NOT NULL UNIQUE,
 description VARCHAR(500) DEFAULT '',icon VARCHAR(30) NOT NULL DEFAULT 'grid',color VARCHAR(7) NOT NULL DEFAULT '#187650',
 macro_group VARCHAR(30) NOT NULL DEFAULT 'OTROS',active BOOLEAN NOT NULL DEFAULT TRUE,version BIGINT NOT NULL DEFAULT 0,
 CHECK(color ~ '^#[0-9A-Fa-f]{6}$')
);
INSERT INTO managed_categories(name,name_key,macro_group) VALUES
 ('Alimentacion','alimentacion','ALIMENTACION'),('Transporte','transporte','TRANSPORTE'),('Gasolina','gasolina','TRANSPORTE'),
 ('Salud','salud','SALUD'),('Servicios','servicios','SERVICIOS'),('Suscripciones','suscripciones','SUSCRIPCIONES'),('Otros','otros','OTROS')
 ON CONFLICT(name_key) DO NOTHING;
-- Preserve historical labels and their associations, including catalog entries outside the original seven.
INSERT INTO managed_categories(name,name_key,macro_group)
 SELECT MIN(trim(category)),translate(lower(regexp_replace(trim(category),'\s+',' ','g')),'áéíóúüñ','aeiouun'),
 CASE WHEN MAX(macro_category) IN ('ALIMENTACION','TRANSPORTE','SALUD','SERVICIOS','SUSCRIPCIONES','OTROS') THEN MAX(macro_category) ELSE 'OTROS' END
 FROM transactions WHERE category IS NOT NULL AND trim(category)<>''
 GROUP BY translate(lower(regexp_replace(trim(category),'\s+',' ','g')),'áéíóúüñ','aeiouun')
 ON CONFLICT(name_key) DO NOTHING;
CREATE TABLE IF NOT EXISTS classification_rules (
 id BIGSERIAL PRIMARY KEY,merchant_key VARCHAR(200) NOT NULL,match_type VARCHAR(10) NOT NULL DEFAULT 'EXACT',
 scope_key VARCHAR(50) NOT NULL DEFAULT 'GLOBAL',user_id BIGINT REFERENCES app_users(id) ON DELETE CASCADE,
 category_id BIGINT NOT NULL REFERENCES managed_categories(id),priority INTEGER NOT NULL DEFAULT 100,active BOOLEAN NOT NULL DEFAULT TRUE,version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(match_type,merchant_key,scope_key),CHECK(match_type IN ('EXACT','PREFIX')),CHECK(priority BETWEEN 0 AND 1000),
 CHECK((scope_key='GLOBAL' AND user_id IS NULL) OR (user_id IS NOT NULL AND scope_key='USER:'||user_id::text))
);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS category_id BIGINT REFERENCES managed_categories(id);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS classification_source VARCHAR(30) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS classification_rule_id BIGINT REFERENCES classification_rules(id);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS review_status VARCHAR(15) NOT NULL DEFAULT 'PENDING';
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS normalized_merchant VARCHAR(500);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS original_description TEXT;
-- Historical currency was not stored: do not invent PEN/USD retrospectively. UNK means not recorded.
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'UNK';
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
UPDATE transactions t SET category_id=c.id FROM managed_categories c WHERE t.category_id IS NULL AND t.classification_source='LEGACY' AND c.name_key=translate(lower(regexp_replace(trim(t.category),'\s+',' ','g')),'áéíóúüñ','aeiouun');
UPDATE transactions SET original_description=description WHERE original_description IS NULL;
UPDATE transactions SET review_status=CASE WHEN category_id IS NULL OR lower(category)='otros' THEN 'PENDING' ELSE 'REVIEWED' END WHERE classification_source='LEGACY';
CREATE TABLE IF NOT EXISTS classification_batches (
 id VARCHAR(36) PRIMARY KEY,actor_id BIGINT NOT NULL REFERENCES app_users(id),kind VARCHAR(30) NOT NULL,status VARCHAR(15) NOT NULL DEFAULT 'PREVIEW',
 rule_id BIGINT REFERENCES classification_rules(id),rule_version BIGINT,target_category_id BIGINT NOT NULL REFERENCES managed_categories(id),target_category_version BIGINT,
 filter_json TEXT NOT NULL,created_at TIMESTAMPTZ NOT NULL,expires_at TIMESTAMPTZ NOT NULL,applied_at TIMESTAMPTZ,reverted_at TIMESTAMPTZ,
 changed_count INTEGER NOT NULL DEFAULT 0,skipped_manual INTEGER NOT NULL DEFAULT 0,version BIGINT NOT NULL DEFAULT 0,
 CHECK(status IN ('PREVIEW','APPLIED','REVERTED'))
);
CREATE TABLE IF NOT EXISTS classification_changes (
 id BIGSERIAL PRIMARY KEY,batch_id VARCHAR(36) NOT NULL REFERENCES classification_batches(id),
 transaction_id BIGINT NOT NULL,before_json TEXT NOT NULL,after_json TEXT NOT NULL,UNIQUE(batch_id,transaction_id)
);
-- Transaction references intentionally survive deletion so the audit remains available.
CREATE TABLE IF NOT EXISTS admin_audit (
 id BIGSERIAL PRIMARY KEY,actor_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL,actor_kind VARCHAR(30) NOT NULL DEFAULT 'ADMIN',
 action VARCHAR(40) NOT NULL,entity_type VARCHAR(30) NOT NULL,entity_id VARCHAR(50) NOT NULL,before_json TEXT,after_json TEXT,
 batch_id VARCHAR(36) REFERENCES classification_batches(id),created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_admin_tx_category_date ON transactions(category_id,date DESC,id);
CREATE INDEX IF NOT EXISTS idx_admin_tx_review ON transactions(review_status,date DESC,id);
CREATE INDEX IF NOT EXISTS idx_admin_rules_scope ON classification_rules(active,user_id);
CREATE INDEX IF NOT EXISTS idx_admin_changes_batch ON classification_changes(batch_id);
CREATE INDEX IF NOT EXISTS idx_admin_audit_date ON admin_audit(created_at DESC,id DESC);
COMMIT;
