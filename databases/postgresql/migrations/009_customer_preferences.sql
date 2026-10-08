CREATE TABLE IF NOT EXISTS customer_preferences (
    preference_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_reference VARCHAR(36) NOT NULL,
    theme VARCHAR(10) NOT NULL DEFAULT 'LIGHT',
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    email_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_customer_preferences_account UNIQUE (account_reference),

    CONSTRAINT fk_customer_preferences_account
        FOREIGN KEY (account_reference)
        REFERENCES accounts(account_reference)
        ON DELETE RESTRICT,

    CONSTRAINT chk_customer_preferences_theme
        CHECK (theme IN ('LIGHT', 'DARK')),

    CONSTRAINT chk_customer_preferences_currency
        CHECK (currency IN ('USD', 'INR'))
);

CREATE INDEX idx_customer_preferences_account_reference
    ON customer_preferences(account_reference);