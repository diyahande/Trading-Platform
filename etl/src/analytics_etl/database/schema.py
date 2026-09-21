from __future__ import annotations

from analytics_etl.config import Settings


def ensure_warehouse_schema(duck_conn, settings: Settings) -> None:
    if not settings.analytics_schema_path.exists():
        raise FileNotFoundError(
            f"Analytics schema file not found: {settings.analytics_schema_path}"
        )
    schema_sql = settings.analytics_schema_path.read_text(encoding="utf-8")
    duck_conn.execute(schema_sql)
    duck_conn.commit()
