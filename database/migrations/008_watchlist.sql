CREATE TABLE IF NOT EXISTS watchlist (
    watchlist_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,

    name VARCHAR(100) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_watchlist_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_watchlist_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instruments(instrument_id)
        ON DELETE CASCADE,

    CONSTRAINT chk_watchlist_name
        CHECK (length(trim(name)) > 0),

    CONSTRAINT uq_watchlist_user_instrument
        UNIQUE (user_id, instrument_id)
);