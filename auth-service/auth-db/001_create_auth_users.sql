CREATE TABLE IF NOT EXISTS auth_users (
    id UUID PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    account_reference UUID NOT NULL,
    roles TEXT[] NOT NULL,
    created_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_auth_users_username UNIQUE (username),
    CONSTRAINT uq_auth_users_account_reference UNIQUE (account_reference),
    -- CONSTRAINT chk_auth_users_username CHECK (username ~ '^[a-zA-Z0-9._-]{3,64}$'),
    CONSTRAINT chk_auth_users_roles_non_empty CHECK (cardinality(roles) > 0),
    CONSTRAINT chk_auth_users_roles_allowed CHECK (
        roles <@ ARRAY['CUSTOMER', 'ADMIN']::TEXT[]
    )
);
