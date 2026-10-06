CREATE TABLE IF NOT EXISTS instruments (
    instrument_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    symbol VARCHAR(30) NOT NULL,

    display_name VARCHAR(200) NOT NULL,

    asset_class VARCHAR(30) NOT NULL,

    quote_currency CHAR(3) NOT NULL,

    is_tradable BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_instruments_symbol
        UNIQUE (symbol),

    CONSTRAINT chk_instruments_symbol
        CHECK (length(trim(symbol)) > 0),

    CONSTRAINT chk_instruments_display_name
        CHECK (length(trim(display_name)) > 0),

    CONSTRAINT chk_instruments_asset_class
        CHECK (
            asset_class IN (
                'EQUITY',
                'ETF',
                'CURRENCY_PAIR',
                'CRYPTO_PAIR'
            )
        ),

    CONSTRAINT chk_instruments_quote_currency
        CHECK (quote_currency ~ '^[A-Z]{3}$')
);