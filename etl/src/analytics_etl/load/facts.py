from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal

from analytics_etl.dead_letter.store import DeadLetterStore
from analytics_etl.transform.trades import transform_order_row


def _next_trade_key(conn) -> int:
    row = conn.execute("SELECT COALESCE(MAX(trade_key), 0) + 1 FROM fact_trades").fetchone()
    return int(row[0])


def _resolve_account_key(conn, source_account_id: int, order_date: date) -> int | None:
    row = conn.execute(
        """
        SELECT account_key
        FROM dim_account
        WHERE source_id = ?
          AND effective_date <= ?
          AND (end_date IS NULL OR end_date >= ?)
        ORDER BY effective_date DESC
        LIMIT 1
        """,
        [source_account_id, order_date, order_date],
    ).fetchone()
    return None if row is None else int(row[0])


def _resolve_instrument_key(conn, symbol: str) -> int | None:
    row = conn.execute(
        "SELECT instrument_key FROM dim_instrument WHERE symbol = ?",
        [symbol],
    ).fetchone()
    return None if row is None else int(row[0])


def _resolve_date_key(conn, order_date: date) -> int | None:
    row = conn.execute(
        "SELECT date_key FROM dim_date WHERE full_date = ?",
        [order_date],
    ).fetchone()
    return None if row is None else int(row[0])


def _exists_source_order(conn, source_order_id: str) -> bool:
    row = conn.execute(
        "SELECT 1 FROM fact_trades WHERE source_order_id = ?",
        [source_order_id],
    ).fetchone()
    return row is not None


def load_fact_trades(
    conn,
    source_orders: list[dict],
    batch_id: str,
    loaded_at: datetime,
    dead_letter_store: DeadLetterStore,
    allowed_sides: set[str],
    allowed_statuses: set[str],
) -> dict:
    inserted = 0
    skipped_duplicate = 0
    dead_lettered = 0

    for raw in source_orders:
        candidate, errors = transform_order_row(raw, allowed_sides, allowed_statuses)
        source_order_id = None if raw.get("order_id") is None else str(raw.get("order_id"))

        if errors:
            dead_letter_store.write(batch_id, source_order_id, "; ".join(errors), raw)
            dead_lettered += 1
            continue

        if _exists_source_order(conn, candidate["source_order_id"]):
            skipped_duplicate += 1
            continue

        order_date = candidate["created_at"].date()

        account_key = _resolve_account_key(conn, candidate["source_account_id"], order_date)
        if account_key is None:
            dead_letter_store.write(
                batch_id,
                candidate["source_order_id"],
                "unresolved account_key",
                raw,
            )
            dead_lettered += 1
            continue

        instrument_key = _resolve_instrument_key(conn, candidate["symbol"])
        if instrument_key is None:
            dead_letter_store.write(
                batch_id,
                candidate["source_order_id"],
                "unresolved instrument_key",
                raw,
            )
            dead_lettered += 1
            continue

        date_key = _resolve_date_key(conn, order_date)
        if date_key is None:
            dead_letter_store.write(
                batch_id,
                candidate["source_order_id"],
                "unresolved date_key",
                raw,
            )
            dead_lettered += 1
            continue

        recomputed_basis = (
            candidate["executed_price"] if candidate["executed_price"] is not None else candidate["price"]
        )
        recomputed_trade_value = (recomputed_basis * Decimal(candidate["quantity"]))
        if recomputed_trade_value.quantize(Decimal("0.01")) != candidate["trade_value"]:
            dead_letter_store.write(
                batch_id,
                candidate["source_order_id"],
                "trade_value validation failed",
                raw,
            )
            dead_lettered += 1
            continue

        trade_key = _next_trade_key(conn)
        conn.execute(
            """
            INSERT INTO fact_trades (
                trade_key, account_key, instrument_key, date_key,
                side, quantity, price, status,
                executed_price, trade_value, source_order_id,
                created_at, loaded_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                trade_key,
                account_key,
                instrument_key,
                date_key,
                candidate["side"],
                candidate["quantity"],
                candidate["price"],
                candidate["status"],
                candidate["executed_price"],
                candidate["trade_value"],
                candidate["source_order_id"],
                candidate["created_at"],
                loaded_at,
            ],
        )
        inserted += 1

    conn.commit()
    return {
        "inserted": inserted,
        "skipped_duplicate": skipped_duplicate,
        "dead_lettered": dead_lettered,
    }
