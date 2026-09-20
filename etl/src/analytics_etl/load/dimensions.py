from __future__ import annotations

from datetime import date, datetime, timedelta


def _next_key(conn, table: str, key_column: str) -> int:
    row = conn.execute(
        f"SELECT COALESCE(MAX({key_column}), 0) + 1 FROM {table}"
    ).fetchone()
    return int(row[0])


def load_dim_date(conn, start_date: date, end_date: date) -> int:
    inserted = 0
    current = start_date

    while current <= end_date:
        date_key = int(current.strftime("%Y%m%d"))
        exists = conn.execute(
            "SELECT 1 FROM dim_date WHERE date_key = ?",
            [date_key],
        ).fetchone()
        if not exists:
            conn.execute(
                """
                INSERT INTO dim_date (
                    date_key, full_date, day, month, year, quarter,
                    day_of_week, day_name, month_name, is_weekday
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                [
                    date_key,
                    current,
                    current.day,
                    current.month,
                    current.year,
                    ((current.month - 1) // 3) + 1,
                    current.isoweekday(),
                    current.strftime("%A"),
                    current.strftime("%B"),
                    current.weekday() < 5,
                ],
            )
            inserted += 1
        current += timedelta(days=1)

    conn.commit()
    return inserted


def _derive_exchange(symbol: str) -> str:
    if symbol.endswith(".NS"):
        return "NSE"
    if symbol.endswith(".BO"):
        return "BSE"
    if symbol.startswith("FX:"):
        return "FX"
    if symbol.startswith("X:"):
        return "CRYPTO"
    return "US"


def load_dim_instrument(conn, instruments: list[dict], loaded_at: datetime) -> dict:
    inserted = 0
    updated = 0

    for row in instruments:
        symbol = row["symbol"]
        existing = conn.execute(
            "SELECT instrument_key FROM dim_instrument WHERE symbol = ?",
            [symbol],
        ).fetchone()

        values = [
            row["display_name"],
            row["asset_class"],
            row["quote_currency"],
            _derive_exchange(symbol),
            bool(row["is_tradable"]),
            loaded_at,
            symbol,
        ]

        if existing:
            conn.execute(
                """
                UPDATE dim_instrument
                SET name = ?,
                    asset_class = ?,
                    currency = ?,
                    exchange = ?,
                    tradable = ?,
                    loaded_at = ?
                WHERE symbol = ?
                """,
                values,
            )
            updated += 1
        else:
            instrument_key = _next_key(conn, "dim_instrument", "instrument_key")
            conn.execute(
                """
                INSERT INTO dim_instrument (
                    instrument_key, symbol, name, asset_class,
                    currency, exchange, tradable, loaded_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                [
                    instrument_key,
                    symbol,
                    row["display_name"],
                    row["asset_class"],
                    row["quote_currency"],
                    _derive_exchange(symbol),
                    bool(row["is_tradable"]),
                    loaded_at,
                ],
            )
            inserted += 1

    conn.commit()
    return {"inserted": inserted, "updated": updated}


def load_dim_account(conn, accounts: list[dict], as_of_date: date, loaded_at: datetime) -> dict:
    inserted = 0
    updated = 0

    for row in accounts:
        source_id = int(row["account_id"])
        current_row = conn.execute(
            """
            SELECT
                account_key,
                holder_name,
                status,
                effective_date
            FROM dim_account
            WHERE source_id = ?
              AND is_current = TRUE
            """,
            [source_id],
        ).fetchone()

        if not current_row:
            account_key = _next_key(conn, "dim_account", "account_key")
            effective_date = row["created_at"].date()
            conn.execute(
                """
                INSERT INTO dim_account (
                    account_key, account_id, holder_name, status,
                    effective_date, end_date, is_current, source_id, loaded_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                [
                    account_key,
                    str(source_id),
                    row["holder_name"],
                    row["status"],
                    effective_date,
                    None,
                    True,
                    source_id,
                    loaded_at,
                ],
            )
            inserted += 1
            continue

        _, current_name, current_status, current_effective = current_row
        if current_name == row["holder_name"] and current_status == row["status"]:
            continue

        close_date = as_of_date - timedelta(days=1)
        if close_date < current_effective:
            close_date = current_effective

        conn.execute(
            """
            UPDATE dim_account
            SET end_date = ?, is_current = FALSE, loaded_at = ?
            WHERE account_key = ?
            """,
            [close_date, loaded_at, current_row[0]],
        )

        next_key = _next_key(conn, "dim_account", "account_key")
        conn.execute(
            """
            INSERT INTO dim_account (
                account_key, account_id, holder_name, status,
                effective_date, end_date, is_current, source_id, loaded_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                next_key,
                str(source_id),
                row["holder_name"],
                row["status"],
                as_of_date,
                None,
                True,
                source_id,
                loaded_at,
            ],
        )
        inserted += 1
        updated += 1

    conn.commit()
    return {"inserted": inserted, "updated": updated}
