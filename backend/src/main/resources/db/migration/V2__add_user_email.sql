ALTER TABLE app_users ADD COLUMN email VARCHAR(254);
CREATE UNIQUE INDEX app_users_email_lower_idx ON app_users (LOWER(email)) WHERE email IS NOT NULL;
