from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timezone
from pathlib import Path
import os


PROJECT_ROOT = Path(__file__).resolve().parents[3]


def _required(name: str) -> str:
	value = os.getenv(name)
	if not value:
		raise RuntimeError(f"{name} is not set. Add it to the repo-root .env.")
	return value


def _load_env_file(path: Path) -> None:
	if not path.exists():
		return
	for line in path.read_text(encoding="utf-8").splitlines():
		stripped = line.strip()
		if not stripped or stripped.startswith("#") or "=" not in stripped:
			continue
		key, value = stripped.split("=", 1)
		key = key.strip()
		value = value.strip()
		if key and key not in os.environ:
			os.environ[key] = value


@dataclass(frozen=True)
class Settings:
	postgres_db: str
	postgres_host: str
	postgres_port: int
	postgres_user: str
	postgres_password: str

	analytics_db_path: Path
	analytics_schema_path: Path
	watermark_path: Path
	dead_letter_path: Path

	dim_date_start: date
	dim_date_end: date

	batch_id: str

	allowed_sides: set[str]
	allowed_statuses: set[str]

	@classmethod
	def from_env(cls) -> "Settings":
		# The repo-root .env is the only env file; real environment variables win.
		_load_env_file(PROJECT_ROOT / ".env")

		batch_default = datetime.now(timezone.utc).strftime("batch_%Y%m%dT%H%M%SZ")

		return cls(
			postgres_db=_required("POSTGRES_DB"),
			postgres_host=_required("PGHOST"),
			postgres_port=int(_required("PGPORT")),
			postgres_user=_required("PGUSER"),
			postgres_password=_required("PGPASSWORD"),
			analytics_db_path=Path(
				os.getenv(
					"ANALYTICS_DUCKDB_PATH",
					str(PROJECT_ROOT / "etl" / "analytics.duckdb"),
				)
			),
			analytics_schema_path=Path(
				os.getenv(
					"ANALYTICS_SCHEMA_PATH",
					str(PROJECT_ROOT / "analytics" / "contracts" / "analytics_schema.sql"),
				)
			),
			watermark_path=Path(
				os.getenv(
					"ETL_WATERMARK_PATH",
					str(PROJECT_ROOT / "etl" / ".state" / "fact_trades.watermark"),
				)
			),
			dead_letter_path=Path(
				os.getenv(
					"ETL_DEAD_LETTER_PATH",
					str(PROJECT_ROOT / "etl" / "dead_letter" / "fact_trades.jsonl"),
				)
			),
			dim_date_start=date.fromisoformat(os.getenv("DIM_DATE_START", "2020-01-01")),
			dim_date_end=date.fromisoformat(os.getenv("DIM_DATE_END", "2035-12-31")),
			batch_id=os.getenv("ETL_BATCH_ID", batch_default),
			allowed_sides={"BUY", "SELL"},
			allowed_statuses={"NEW", "FILLED", "REJECTED", "CANCELLED"},
		)

