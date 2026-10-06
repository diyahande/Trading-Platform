CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_on TIMESTAMPTZ NOT NULL,
    consumed_on TIMESTAMPTZ,
    revoked_on TIMESTAMPTZ,
    created_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_auth_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES auth_users(id)
        ON DELETE CASCADE
);
