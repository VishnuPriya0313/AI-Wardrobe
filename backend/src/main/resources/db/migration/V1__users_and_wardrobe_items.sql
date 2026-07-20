CREATE TABLE app_users (
  id UUID PRIMARY KEY,
  username VARCHAR(40) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE wardrobe_items (
  id VARCHAR(100) NOT NULL,
  user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  image_fingerprint VARCHAR(128),
  original_file_name VARCHAR(255),
  category VARCHAR(40) NOT NULL,
  analysis_json TEXT NOT NULL,
  image_key VARCHAR(500) NOT NULL,
  metadata_key VARCHAR(500) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (id, user_id)
);

CREATE INDEX wardrobe_items_user_created_idx ON wardrobe_items(user_id, created_at DESC);
