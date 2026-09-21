from __future__ import annotations

import json
from datetime import UTC, date, datetime
from decimal import Decimal
from pathlib import Path


def _json_default(value):
    if isinstance(value, (datetime, date)):
        return value.isoformat()
    if isinstance(value, Decimal):
        return str(value)
    return str(value)


class DeadLetterStore:
    def __init__(self, file_path: Path):
        self._file_path = Path(file_path)
        self._file_path.parent.mkdir(parents=True, exist_ok=True)

    def write(self, batch_id: str, source_order_id: str | None, reason: str, payload: dict) -> None:
        row = {
            "batch_id": batch_id,
            "source_order_id": source_order_id,
            "reason": reason,
            "failed_at": datetime.now(UTC).isoformat(),
            "payload": payload,
        }
        with self._file_path.open("a", encoding="utf-8") as fh:
            fh.write(json.dumps(row, default=_json_default) + "\n")

    def read_all(self) -> list[dict]:
        if not self._file_path.exists():
            return []
        rows: list[dict] = []
        for line in self._file_path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                rows.append(json.loads(line))
        return rows
