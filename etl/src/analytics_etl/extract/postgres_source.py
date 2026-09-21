from __future__ import annotations

from datetime import datetime

from analytics_etl.database.connections import dict_cursor


class PostgresSource:
    """Postgres-backed source used by the ETL pipeline."""

    def __init__(self, pg_conn):
        self._pg_conn = pg_conn

    def fetch_order_created_bounds(self) -> tuple[datetime | None, datetime | None]:
        with dict_cursor(self._pg_conn) as cur:
            cur.execute("SELECT MIN(created_at) AS min_created, MAX(created_at) AS max_created FROM orders")
            row = cur.fetchone()
        return row["min_created"], row["max_created"]

    def fetch_instruments(self) -> list[dict]:
        with dict_cursor(self._pg_conn) as cur:
            cur.execute(
                """
                SELECT
                    instrument_id,
                    symbol,
                    display_name,
                    asset_class,
                    quote_currency,
                    is_tradable
                FROM instruments
                ORDER BY instrument_id
                """
            )
            return list(cur.fetchall())

    def fetch_accounts(self) -> list[dict]:
        with dict_cursor(self._pg_conn) as cur:
            cur.execute(
                """
                SELECT
                    account_id,
                    account_reference,
                    holder_name,
                    status,
                    created_at
                FROM accounts
                ORDER BY account_id
                """
            )
            return list(cur.fetchall())

    def fetch_orders_since(self, watermark: datetime | None) -> list[dict]:
        executed_price_expr = "NULL::numeric AS executed_price"
        if self._has_order_column("executed_price"):
            executed_price_expr = "o.executed_price"

        with dict_cursor(self._pg_conn) as cur:
            if watermark is None:
                cur.execute(
                    f"""
                    SELECT
                        o.order_id,
                        o.account_id,
                        o.instrument_id,
                        i.symbol,
                        o.side,
                        o.quantity,
                        o.price,
                        o.status,
                        {executed_price_expr},
                        o.created_at
                    FROM orders o
                    JOIN instruments i
                      ON i.instrument_id = o.instrument_id
                    ORDER BY o.created_at, o.order_id
                    """
                )
            else:
                cur.execute(
                    f"""
                    SELECT
                        o.order_id,
                        o.account_id,
                        o.instrument_id,
                        i.symbol,
                        o.side,
                        o.quantity,
                        o.price,
                        o.status,
                        {executed_price_expr},
                        o.created_at
                    FROM orders o
                    JOIN instruments i
                      ON i.instrument_id = o.instrument_id
                    WHERE o.created_at > %s
                    ORDER BY o.created_at, o.order_id
                    """,
                    [watermark],
                )
            return list(cur.fetchall())

    def _has_order_column(self, column_name: str) -> bool:
        with dict_cursor(self._pg_conn) as cur:
            cur.execute(
                """
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'orders'
                  AND column_name = %s
                """,
                [column_name],
            )
            return cur.fetchone() is not None
