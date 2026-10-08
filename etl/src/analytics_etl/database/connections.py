from __future__ import annotations

from contextlib import contextmanager
from pathlib import Path

import duckdb
import psycopg2
from psycopg2.extras import RealDictCursor

from analytics_etl.config import Settings


def connect_duckdb(settings: Settings) -> duckdb.DuckDBPyConnection:
    settings.analytics_db_path.parent.mkdir(parents=True, exist_ok=True)
    return duckdb.connect(str(settings.analytics_db_path))


@contextmanager
def connect_postgres(settings: Settings):
    conn = psycopg2.connect(
        dbname=settings.postgres_db,
        host=settings.postgres_host,
        port=settings.postgres_port,
        user=settings.postgres_user,
        password=settings.postgres_password,
    )
    try:
        yield conn
    finally:
        conn.close()


@contextmanager
def dict_cursor(pg_conn):
    cur = pg_conn.cursor(cursor_factory=RealDictCursor)
    try:
        yield cur
    finally:
        cur.close()
