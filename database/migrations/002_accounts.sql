CREATE TABLE IF NOT EXISTS accounts (
    account_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id BIGINT NOT NULL,

    account_reference VARCHAR(36) NOT NULL,

    holder_name VARCHAR(100) NOT NULL,

    currency CHAR(3) NOT NULL DEFAULT 'USD',

    cash_balance NUMERIC(20, 4) NOT NULL DEFAULT 0,

    version BIGINT NOT NULL DEFAULT 0,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_accounts_reference
        UNIQUE (account_reference),

    CONSTRAINT uq_accounts_id_reference
        UNIQUE (account_id, account_reference),

    CONSTRAINT fk_accounts_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_accounts_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),

    CONSTRAINT chk_accounts_cash_balance
        CHECK (cash_balance >= 0),

    CONSTRAINT chk_accounts_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT chk_accounts_holder_name
        CHECK (length(trim(holder_name)) > 0)

    ,CONSTRAINT chk_accounts_reference_uuid
        CHECK (
            account_reference ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
        )
);

CREATE INDEX idx_accounts_user_id
    ON accounts(user_id);