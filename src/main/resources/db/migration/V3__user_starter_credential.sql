-- Starter credential issued by clinic admins (first sign-in helper for staff and patients).
ALTER TABLE app_user ADD COLUMN tmp_pw_hash VARCHAR(255);
ALTER TABLE app_user ADD COLUMN tmp_pw_issued_at TIMESTAMP;
