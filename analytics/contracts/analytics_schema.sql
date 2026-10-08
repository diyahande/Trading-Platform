-- =============================================================================
-- Enterprise Trading Platform: Analytical Schema
-- =============================================================================
--
-- Purpose:
--   Analytical dimensional model for Sprint 4 and Sprint 7.
--
-- Sprint 4:
--   ETL loads:
--       1. DIM_INSTRUMENT
--       2. FACT_CANDLES
--
--   Instrument data:
--       GET /symbols/{symbol}
--
--   Candle data:
--       GET /candles/{symbol}
--
--   Only candle data passes through transform.py.
--
-- Sprint 7:
--   Original trading tables are retained:
--       DIM_DATE
--       DIM_INSTRUMENT
--       DIM_ACCOUNT
--       FACT_TRADES
--
-- =============================================================================


-- =============================================================================
-- DIM_ACCOUNT
-- =============================================================================

CREATE TABLE IF NOT EXISTS dim_account (
    account_key     BIGINT       NOT NULL,
    account_id      VARCHAR(32)  NOT NULL,
    holder_name     VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    effective_date  DATE         NOT NULL,

    end_date        DATE,
    is_current      BOOLEAN      NOT NULL,
    source_id       BIGINT       NOT NULL,
    loaded_at       TIMESTAMP    NOT NULL,

    CONSTRAINT pk_dim_account
        PRIMARY KEY (account_key)
);


-- =============================================================================
-- DIM_INSTRUMENT
-- =============================================================================
--
-- Source:
--   GET /symbols/{symbol}
--
-- =============================================================================

CREATE TABLE IF NOT EXISTS dim_instrument (
    instrument_key  BIGINT       NOT NULL,
    symbol          VARCHAR(20)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    asset_class     VARCHAR(20)  NOT NULL,
    currency        CHAR(3)      NOT NULL,

    exchange        VARCHAR(20),
    tradable        BOOLEAN      NOT NULL,
    loaded_at       TIMESTAMP    NOT NULL,

    CONSTRAINT pk_dim_instrument
        PRIMARY KEY (instrument_key),

    CONSTRAINT uq_dim_instrument_symbol
        UNIQUE (symbol)
);


-- =============================================================================
-- DIM_DATE
-- =============================================================================
--
-- Retained for Sprint 7.
-- Sprint 4 does not require this table for candle analytics.
--
-- =============================================================================

CREATE TABLE IF NOT EXISTS dim_date (
    date_key       INTEGER      NOT NULL,
    full_date      DATE         NOT NULL,
    day            INTEGER      NOT NULL,
    month          INTEGER      NOT NULL,
    year           INTEGER      NOT NULL,
    quarter        INTEGER      NOT NULL,

    day_of_week    INTEGER      NOT NULL,
    day_name       VARCHAR(9)   NOT NULL,
    month_name     VARCHAR(9)   NOT NULL,
    is_weekday     BOOLEAN      NOT NULL,

    CONSTRAINT pk_dim_date
        PRIMARY KEY (date_key),

    CONSTRAINT uq_dim_date_full_date
        UNIQUE (full_date)
);


-- =============================================================================
-- FACT_TRADES
-- =============================================================================
--
-- Retained for Sprint 7.
--
-- =============================================================================

CREATE TABLE IF NOT EXISTS fact_trades (
    trade_key       BIGINT        NOT NULL,
    account_key     BIGINT        NOT NULL,
    instrument_key  BIGINT        NOT NULL,
    date_key        INTEGER       NOT NULL,

    side            VARCHAR(4)     NOT NULL,
    quantity        INTEGER        NOT NULL,
    price           DECIMAL(18,2)  NOT NULL,
    status          VARCHAR(20)    NOT NULL,

    executed_price  DECIMAL(18,2),
    trade_value     DECIMAL(18,2)  NOT NULL,
    source_order_id VARCHAR(36)     NOT NULL,

    created_at      TIMESTAMP      NOT NULL,
    loaded_at       TIMESTAMP      NOT NULL,

    CONSTRAINT pk_fact_trades
        PRIMARY KEY (trade_key),

    CONSTRAINT uq_fact_trades_source
        UNIQUE (source_order_id),

    CONSTRAINT fk_fact_trades_account
        FOREIGN KEY (account_key)
        REFERENCES dim_account (account_key),

    CONSTRAINT fk_fact_trades_instrument
        FOREIGN KEY (instrument_key)
        REFERENCES dim_instrument (instrument_key),

    CONSTRAINT fk_fact_trades_date
        FOREIGN KEY (date_key)
        REFERENCES dim_date (date_key)
);


-- =============================================================================
-- FACT_CANDLES
-- =============================================================================
--
-- Sprint 4 market-data fact table.
--
-- Grain:
--   One row per instrument per trading date.
--
-- Source:
--   GET /candles/{symbol}
--
-- =============================================================================

CREATE TABLE IF NOT EXISTS fact_candles (
    candle_key       BIGINT         NOT NULL,

    instrument_key   BIGINT         NOT NULL,

    candle_date      DATE           NOT NULL,

    open_price       DECIMAL(18,2)  NOT NULL,
    high_price       DECIMAL(18,2)  NOT NULL,
    low_price        DECIMAL(18,2)  NOT NULL,
    close_price      DECIMAL(18,2)  NOT NULL,
    adjclose_price   DECIMAL(18,2)  NOT NULL,

    volume           DECIMAL(20,2),

    synthetic        BOOLEAN        NOT NULL,

    daily_return     DECIMAL(18,8),
    price_range      DECIMAL(18,2),
    price_range_pct  DECIMAL(18,8),

    loaded_at        TIMESTAMP      NOT NULL,

    CONSTRAINT pk_fact_candles
        PRIMARY KEY (candle_key),

    CONSTRAINT uq_fact_candles_instrument_date
        UNIQUE (instrument_key, candle_date),

    CONSTRAINT fk_fact_candles_instrument
        FOREIGN KEY (instrument_key)
        REFERENCES dim_instrument (instrument_key)
);


-- =============================================================================
-- SPRINT 4 ANALYTICS
-- =============================================================================
--
-- Available analytical measures:
--
--   close_price
--   volume
--   daily_return
--   price_range
--   price_range_pct
--
-- Dimensions:
--
--   symbol
--   name
--   asset_class
--   exchange
--   currency
--   candle_date
--
-- =============================================================================