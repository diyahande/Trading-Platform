CREATE TABLE IF NOT EXISTS transactions (
    transaction_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    order_id BIGINT NOT NULL,

    instrument_id BIGINT NOT NULL,

    quantity NUMERIC(20, 8) NOT NULL,

    price NUMERIC(20, 8) NOT NULL,

    executed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',

    CONSTRAINT fk_transactions_order
        FOREIGN KEY (order_id)
        REFERENCES orders(order_id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_transactions_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instruments(instrument_id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_transactions_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_transactions_price
        CHECK (price > 0),

    CONSTRAINT chk_transactions_status
        CHECK (
            status IN (
                'COMPLETED',
                'FAILED'
            )
        )
);

CREATE INDEX idx_transactions_order_id
    ON transactions(order_id);

CREATE INDEX idx_transactions_instrument_id
    ON transactions(instrument_id);