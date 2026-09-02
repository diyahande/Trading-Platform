CREATE TABLE IF NOT EXISTS portfolios (
    portfolio_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    account_id BIGINT NOT NULL,

    current_value NUMERIC(20, 4) NOT NULL DEFAULT 0,

    invested_value NUMERIC(20, 4) NOT NULL DEFAULT 0,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_portfolios_account
        UNIQUE (account_id),

    CONSTRAINT fk_portfolios_account
        FOREIGN KEY (account_id)
        REFERENCES accounts(account_id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_portfolios_current_value
        CHECK (current_value >= 0),

    CONSTRAINT chk_portfolios_invested_value
        CHECK (invested_value >= 0)
);


CREATE TABLE IF NOT EXISTS portfolio_holdings (
    holding_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    portfolio_id BIGINT NOT NULL,

    instrument_id BIGINT NOT NULL,

    quantity NUMERIC(20, 8) NOT NULL DEFAULT 0,

    average_price NUMERIC(20, 8) NOT NULL DEFAULT 0,

    last_updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_holdings_portfolio
        FOREIGN KEY (portfolio_id)
        REFERENCES portfolios(portfolio_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_holdings_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instruments(instrument_id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_portfolio_instrument
        UNIQUE (portfolio_id, instrument_id),

    CONSTRAINT chk_holdings_quantity
        CHECK (quantity >= 0),

    CONSTRAINT chk_holdings_average_price
        CHECK (average_price >= 0)
);

CREATE INDEX idx_holdings_portfolio_id
    ON portfolio_holdings(portfolio_id);

CREATE INDEX idx_holdings_instrument_id
    ON portfolio_holdings(instrument_id);