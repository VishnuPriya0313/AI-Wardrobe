ALTER TABLE app_users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE app_users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE app_users ALTER COLUMN email_verified SET DEFAULT FALSE;
ALTER TABLE app_users ADD COLUMN verification_token_hash VARCHAR(64);
ALTER TABLE app_users ADD COLUMN verification_expires_at TIMESTAMPTZ;
ALTER TABLE app_users ADD COLUMN oauth_provider VARCHAR(32);
ALTER TABLE app_users ADD COLUMN oauth_subject VARCHAR(255);

CREATE UNIQUE INDEX app_users_oauth_identity_idx
  ON app_users (oauth_provider, oauth_subject)
  WHERE oauth_provider IS NOT NULL AND oauth_subject IS NOT NULL;
