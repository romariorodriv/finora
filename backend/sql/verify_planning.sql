-- LOCAL TEST ONLY: use an empty disposable database. Never run against production.
\set ON_ERROR_STOP on
CREATE TABLE app_users (id BIGSERIAL PRIMARY KEY);
\ir V2_2__planning.sql
\ir V2_2__planning.sql
INSERT INTO app_users DEFAULT VALUES;
INSERT INTO planning_profiles(user_id,confirmed_balance,reserve_amount,next_income_date) VALUES(1,1000,50,CURRENT_DATE+10);
INSERT INTO planning_payments(user_id,name,due_date,amount) VALUES(1,'Alquiler',CURRENT_DATE+1,300);
DO $$ BEGIN
  BEGIN
    INSERT INTO planning_payments(user_id,name,due_date,amount) VALUES(999,'Sin usuario',CURRENT_DATE,10);
    RAISE EXCEPTION 'Missing payment user foreign key';
  EXCEPTION WHEN foreign_key_violation THEN NULL; END;
  BEGIN
    INSERT INTO planning_profiles(user_id,confirmed_balance,reserve_amount,next_income_date) VALUES(999,100,0,CURRENT_DATE+1);
    RAISE EXCEPTION 'Missing profile user foreign key';
  EXCEPTION WHEN foreign_key_violation THEN NULL; END;
  BEGIN
    INSERT INTO planning_profiles(user_id,confirmed_balance,reserve_amount,next_income_date) VALUES(1,100,0,CURRENT_DATE+1);
    RAISE EXCEPTION 'Missing unique user profile';
  EXCEPTION WHEN unique_violation THEN NULL; END;
  BEGIN
    INSERT INTO planning_payments(user_id,name,due_date,amount) VALUES(1,'Negativo',CURRENT_DATE,-1);
    RAISE EXCEPTION 'Missing positive payment check';
  EXCEPTION WHEN check_violation THEN NULL; END;
  BEGIN
    UPDATE planning_profiles SET reserve_amount=-1 WHERE user_id=1;
    RAISE EXCEPTION 'Missing nonnegative reserve check';
  EXCEPTION WHEN check_violation THEN NULL; END;
END $$;
SELECT conrelid::regclass,contype,pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid IN ('planning_profiles'::regclass,'planning_payments'::regclass) ORDER BY 1,2;
DELETE FROM app_users WHERE id=1;
DO $$ BEGIN
 IF EXISTS(SELECT FROM planning_profiles) OR EXISTS(SELECT FROM planning_payments) THEN RAISE EXCEPTION 'Missing cascade'; END IF;
END $$;
SELECT 'PASS: migration repeatable; FK, unique, amount checks and cascade verified' AS result;
