from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path

import duckdb

from analytics_etl.config import Settings
from analytics_etl.pipeline import run_dimensions, run_facts
from analytics_etl.transform.trades import transform_order_row


TEST_SCHEMA = """
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
    CONSTRAINT pk_dim_account PRIMARY KEY (account_key)
);

CREATE TABLE IF NOT EXISTS dim_instrument (
    instrument_key  BIGINT       NOT NULL,
    symbol          VARCHAR(20)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    asset_class     VARCHAR(20)  NOT NULL,
    currency        CHAR(3)      NOT NULL,
    exchange        VARCHAR(20),
    tradable        BOOLEAN      NOT NULL,
    loaded_at       TIMESTAMP    NOT NULL,
    CONSTRAINT pk_dim_instrument PRIMARY KEY (instrument_key),
    CONSTRAINT uq_dim_instrument_symbol UNIQUE (symbol)
);

CREATE TABLE IF NOT EXISTS dim_date (
    date_key    INTEGER     NOT NULL,
    full_date   DATE        NOT NULL,
    day         INTEGER     NOT NULL,
    month       INTEGER     NOT NULL,
    year        INTEGER     NOT NULL,
    quarter     INTEGER     NOT NULL,
    day_of_week INTEGER     NOT NULL,
    day_name    VARCHAR(9)  NOT NULL,
    month_name  VARCHAR(9)  NOT NULL,
    is_weekday  BOOLEAN     NOT NULL,
    CONSTRAINT pk_dim_date PRIMARY KEY (date_key),
    CONSTRAINT uq_dim_date_full_date UNIQUE (full_date)
);

CREATE TABLE IF NOT EXISTS fact_trades (
    trade_key       BIGINT        NOT NULL,
    account_key     BIGINT        NOT NULL,
    instrument_key  BIGINT        NOT NULL,
    date_key        INTEGER       NOT NULL,
    side            VARCHAR(4)    NOT NULL,
    quantity        INTEGER       NOT NULL,
    price           DECIMAL(18,2) NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    executed_price  DECIMAL(18,2),
    trade_value     DECIMAL(18,2) NOT NULL,
    source_order_id VARCHAR(36)   NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    loaded_at       TIMESTAMP     NOT NULL,
    CONSTRAINT pk_fact_trades PRIMARY KEY (trade_key),
    CONSTRAINT uq_fact_trades_source UNIQUE (source_order_id)
);
"""


class FakeSource:
    def __init__(self, instruments: list[dict], accounts: list[dict], orders: list[dict]):
        self._instruments = instruments
        self._accounts = accounts
        self._orders = orders

    def fetch_order_created_bounds(self):
        if not self._orders:
            return None, None
        created_values = [row["created_at"] for row in self._orders]
        return min(created_values), max(created_values)

    def fetch_instruments(self):
        return self._instruments

    def fetch_accounts(self):
        return self._accounts

    def fetch_orders_since(self, watermark):
        if watermark is None:
            return list(self._orders)
        return [row for row in self._orders if row["created_at"] > watermark]


def _settings(tmp_path: Path) -> Settings:
    schema_path = tmp_path / "schema.sql"
    schema_path.write_text(TEST_SCHEMA, encoding="utf-8")

    return Settings(
        postgres_db="unused",
        postgres_host="unused",
        postgres_port=5432,
        postgres_user="unused",
        postgres_password="unused",
        analytics_db_path=tmp_path / "warehouse.duckdb",
        analytics_schema_path=schema_path,
        watermark_path=tmp_path / "state" / "watermark.txt",
        dead_letter_path=tmp_path / "dead_letter" / "fact_trades.jsonl",
        dim_date_start=datetime(2020, 1, 1, tzinfo=timezone.utc).date(),
        dim_date_end=datetime(2035, 12, 31, tzinfo=timezone.utc).date(),
        batch_id="batch_test",
        allowed_sides={"BUY", "SELL"},
        allowed_statuses={"NEW", "FILLED", "REJECTED", "CANCELLED"},
    )


def _sample_entities():
    created_at = datetime(2026, 9, 17, 10, 0, 0, tzinfo=timezone.utc)
    instruments = [
        {
            "instrument_id": 1,
            "symbol": "AAPL",
            "display_name": "Apple Inc",
            "asset_class": "EQUITY",
            "quote_currency": "USD",
            "is_tradable": True,
        }
    ]
    accounts = [
        {
            "account_id": 100,
            "account_reference": "a-ref",
            "holder_name": "Alice",
            "status": "ACTIVE",
            "created_at": created_at,
        }
    ]
    orders = [
        {
            "order_id": "11111111-1111-1111-1111-111111111111",
            "account_id": 100,
            "instrument_id": 1,
            "symbol": "AAPL",
            "side": "BUY",
            "quantity": 10,
            "price": "100.00",
            "status": "NEW",
            "executed_price": None,
            "created_at": created_at,
        }
    ]
    return instruments, accounts, orders


def test_incremental_load_populates_fact_trades(tmp_path: Path):
    settings = _settings(tmp_path)
    instruments, accounts, orders = _sample_entities()
    source = FakeSource(instruments, accounts, orders)

    conn = duckdb.connect(str(settings.analytics_db_path))
    try:
        run_dimensions(settings, source, conn)
        result = run_facts(settings, source, conn)
        count = conn.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
    finally:
        conn.close()

    assert result["inserted"] == 1
    assert count == 1


def test_second_load_with_no_new_data_adds_no_rows(tmp_path: Path):
    settings = _settings(tmp_path)
    instruments, accounts, orders = _sample_entities()
    source = FakeSource(instruments, accounts, orders)

    conn = duckdb.connect(str(settings.analytics_db_path))
    try:
        run_dimensions(settings, source, conn)
        run_facts(settings, source, conn)
        first_count = conn.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]

        second_result = run_facts(settings, source, conn)
        second_count = conn.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
    finally:
        conn.close()

    assert first_count == 1
    assert second_count == 1
    assert second_result["source_rows"] == 0
    assert second_result["inserted"] == 0


def test_transform_handles_nulls_and_type_mismatches():
    bad_row = {
        "order_id": "22222222-2222-2222-2222-222222222222",
        "account_id": 200,
        "instrument_id": 2,
        "symbol": "MSFT",
        "side": "BUY",
        "quantity": "not-a-number",
        "price": None,
        "status": "FILLED",
        "executed_price": None,
        "created_at": "2026-09-17T10:00:00Z",
    }

    candidate, errors = transform_order_row(
        bad_row,
        allowed_sides={"BUY", "SELL"},
        allowed_statuses={"NEW", "FILLED", "REJECTED", "CANCELLED"},
    )

    assert candidate is None
    assert any("quantity is not numeric" in message for message in errors)
    assert any("price is null" in message for message in errors)
    assert any("created_at is not a datetime" in message for message in errors)


def test_invalid_row_dead_lettered_and_load_continues(tmp_path: Path):
    settings = _settings(tmp_path)
    instruments, accounts, orders = _sample_entities()

    invalid = {
        "order_id": "33333333-3333-3333-3333-333333333333",
        "account_id": 100,
        "instrument_id": 1,
        "symbol": "AAPL",
        "side": "HOLD",
        "quantity": 10,
        "price": "120.00",
        "status": "NEW",
        "executed_price": None,
        "created_at": datetime(2026, 9, 17, 10, 5, 0, tzinfo=timezone.utc),
    }
    source = FakeSource(instruments, accounts, orders + [invalid])

    conn = duckdb.connect(str(settings.analytics_db_path))
    try:
        run_dimensions(settings, source, conn)
        result = run_facts(settings, source, conn)
        count = conn.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
    finally:
        conn.close()

    assert count == 1
    assert result["dead_lettered"] == 1

    lines = settings.dead_letter_path.read_text(encoding="utf-8").splitlines()
    assert len(lines) == 1
    assert "batch_test" in lines[0]
    assert "side must be one of" in lines[0]
