-- LOCAL VERIFICATION ONLY. Use an empty, disposable PostgreSQL database.
\set ON_ERROR_STOP on
CREATE TABLE app_users(id BIGSERIAL PRIMARY KEY,email VARCHAR(255) NOT NULL UNIQUE);
CREATE TABLE transactions(id BIGSERIAL PRIMARY KEY,user_id BIGINT NOT NULL,description VARCHAR(255) NOT NULL,category VARCHAR(255),macro_category VARCHAR(255),type VARCHAR(255),amount NUMERIC(12,2),date DATE,merchant VARCHAR(255));
INSERT INTO app_users(email) VALUES('owner@local.test'),('other@local.test');
INSERT INTO transactions(user_id,description,category,macro_category,type,amount,date,merchant) VALUES
 (1,'Descripción original A','Gasolina','TRANSPORTE','EXPENSE',10,CURRENT_DATE,'ALLE ESTACIONES SAC'),
 (1,'Descripción original B','Legado QA','OTROS','EXPENSE',20,CURRENT_DATE,'Local QA'),
 (2,'Descripción original C','Otros','OTROS','EXPENSE',30,CURRENT_DATE,'Pendiente QA');
\ir V2_3__admin_classification.sql
\ir V2_3__admin_classification.sql
DO $$ BEGIN
 IF (SELECT count(*) FROM transactions)<>3 THEN RAISE EXCEPTION 'Historical transactions changed'; END IF;
 IF EXISTS(SELECT 1 FROM transactions WHERE category_id IS NULL OR original_description<>description OR currency<>'UNK') THEN RAISE EXCEPTION 'Legacy metadata not preserved'; END IF;
 IF EXISTS(SELECT 1 FROM app_users WHERE role<>'USER') THEN RAISE EXCEPTION 'Unsafe default role'; END IF;
 IF NOT EXISTS(SELECT 1 FROM transactions t JOIN managed_categories c ON c.id=t.category_id WHERE t.category='Gasolina' AND c.name='Gasolina') THEN RAISE EXCEPTION 'Gasolina association missing'; END IF;
 BEGIN UPDATE app_users SET role='ROOT' WHERE id=1; RAISE EXCEPTION 'Missing role constraint'; EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN UPDATE transactions SET category_id=99999 WHERE id=1; RAISE EXCEPTION 'Missing category FK'; EXCEPTION WHEN foreign_key_violation THEN NULL; END;
 BEGIN INSERT INTO classification_rules(merchant_key,category_id,scope_key,user_id) VALUES('qa',1,'GLOBAL',1); RAISE EXCEPTION 'Missing scope constraint'; EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN INSERT INTO managed_categories(name,name_key) VALUES('Duplicada','gasolina'); RAISE EXCEPTION 'Missing category uniqueness'; EXCEPTION WHEN unique_violation THEN NULL; END;
END $$;
\set admin_email 'owner@local.test'
\ir grant_admin.sql
DO $$ BEGIN
 IF (SELECT role FROM app_users WHERE email='owner@local.test')<>'ADMIN' THEN RAISE EXCEPTION 'Grant procedure failed'; END IF;
 IF (SELECT role FROM app_users WHERE email='other@local.test')<>'USER' THEN RAISE EXCEPTION 'Grant exceeded requested account'; END IF;
 IF NOT EXISTS(SELECT 1 FROM admin_audit WHERE action='ROLE_GRANT' AND actor_kind='DATABASE_OPERATOR') THEN RAISE EXCEPTION 'Missing grant audit'; END IF;
END $$;
SELECT conrelid::regclass,contype,pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid IN ('managed_categories'::regclass,'classification_rules'::regclass,'classification_batches'::regclass,'classification_changes'::regclass,'admin_audit'::regclass,'transactions'::regclass,'app_users'::regclass) ORDER BY 1,2;
SELECT 'PASS: migration repeatable; data, categories, original descriptions and unknown currency preserved; role/FK/scope/unique constraints and explicit admin grant verified.' AS result;
