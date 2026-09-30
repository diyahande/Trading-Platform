CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_user_id
    ON auth_refresh_tokens(user_id);
