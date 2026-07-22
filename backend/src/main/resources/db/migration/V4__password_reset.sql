ALTER TABLE app_users ADD COLUMN password_reset_token_hash VARCHAR(64);
ALTER TABLE app_users ADD COLUMN password_reset_expires_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_app_users_password_reset_token
  ON app_users(password_reset_token_hash)
  WHERE password_reset_token_hash IS NOT NULL;
