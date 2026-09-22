-- Run with psql against Identity DB after Flyway V2.
-- This is an operator-invoked offline import, NOT an automatic Flyway migration.
-- Requires users.csv in psql's current working directory; export instructions in README.
\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE legacy_users (
 id UUID, email TEXT, hashed_password TEXT, is_active BOOLEAN,
 full_name TEXT, role TEXT, created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ
);
\copy legacy_users FROM 'users.csv' WITH (FORMAT csv, HEADER true)
DO $$
BEGIN
 IF EXISTS (SELECT 1 FROM users) THEN
   RAISE EXCEPTION 'Import requires an empty Identity users table; disable bootstrap first';
 END IF;
 IF EXISTS (SELECT 1 FROM legacy_users WHERE id IS NULL OR email IS NULL
    OR length(trim(email))=0 OR length(trim(email))>254
    OR hashed_password IS NULL OR hashed_password NOT LIKE '$2%'
    OR role IS NULL OR upper(role) NOT IN ('USER','ADMIN')
    OR is_active IS NULL OR created_at IS NULL OR updated_at IS NULL) THEN
   RAISE EXCEPTION 'Invalid legacy account data; audit before import';
 END IF;
 IF EXISTS (SELECT lower(trim(email)) FROM legacy_users GROUP BY lower(trim(email)) HAVING count(*)>1) THEN
   RAISE EXCEPTION 'Email collisions after normalization';
 END IF;
END $$;
INSERT INTO users(id,email,full_name,is_active,created_at,updated_at)
 SELECT id,lower(trim(email)),full_name,is_active,created_at,updated_at FROM legacy_users;
INSERT INTO user_credentials(user_id,password_hash,password_changed_at)
 SELECT id,hashed_password,updated_at FROM legacy_users;
INSERT INTO user_roles(user_id,role_id)
 SELECT id,CASE upper(role) WHEN 'ADMIN' THEN 2 ELSE 1 END FROM legacy_users;
-- Sessions deliberately not imported: legacy JWTs/refresh tokens are incompatible.
COMMIT;
