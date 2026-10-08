CREATE TABLE IF NOT EXISTS orders (
    order_id UUID PRIMARY KEY,

    account_id BIGINT NOT NULL,

    account_reference VARCHAR(36) NOT NULL,

    instrument_id BIGINT NOT NULL,

    side VARCHAR(10) NOT NULL,

    quantity NUMERIC(20, 8) NOT NULL,

    price NUMERIC(20, 8) NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'NEW',

    idempotency_key VARCHAR(255) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_orders_idempotency_key
        UNIQUE (idempotency_key),

    CONSTRAINT fk_orders_account
        FOREIGN KEY (account_id, account_reference)
        REFERENCES accounts(account_id, account_reference)
        ON DELETE RESTRICT,

    CONSTRAINT fk_orders_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instruments(instrument_id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_orders_side
        CHECK (side IN ('BUY', 'SELL')),

    CONSTRAINT chk_orders_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_orders_price
        CHECK (price > 0),

    CONSTRAINT chk_orders_status
        CHECK (
            status IN (
                'NEW',
                'FILLED',
                'REJECTED',
                'CANCELLED'
            )
        )
);

CREATE INDEX idx_orders_account_status_created
    ON orders(account_id, status, created_at DESC);

CREATE INDEX idx_orders_account_created
    ON orders(account_id, created_at DESC);

CREATE INDEX idx_orders_created_at
    ON orders(created_at);