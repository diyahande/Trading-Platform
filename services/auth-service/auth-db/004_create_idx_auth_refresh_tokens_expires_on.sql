CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_expires_on
    ON auth_refresh_tokens(expires_on);
