from __future__ import annotations

from datetime import datetime

from analytics_etl.config import Settings


def read_watermark(settings: Settings) -> datetime | None:
    if not settings.watermark_path.exists():
        return None
    raw = settings.watermark_path.read_text(encoding="utf-8").strip()
    if not raw:
        return None
    return datetime.fromisoformat(raw)


def write_watermark(settings: Settings, watermark: datetime) -> None:
    settings.watermark_path.parent.mkdir(parents=True, exist_ok=True)
    settings.watermark_path.write_text(watermark.isoformat(), encoding="utf-8")
