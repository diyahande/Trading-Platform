from __future__ import annotations

import argparse
from datetime import datetime, timezone

from analytics_etl.config import Settings
from analytics_etl.database.connections import connect_duckdb, connect_postgres
from analytics_etl.database.schema import ensure_warehouse_schema
from analytics_etl.database.watermark import read_watermark, write_watermark
from analytics_etl.dead_letter.store import DeadLetterStore
from analytics_etl.extract.postgres_source import PostgresSource
from analytics_etl.load.dimensions import load_dim_account, load_dim_date, load_dim_instrument
from analytics_etl.load.facts import load_fact_trades


def _determine_date_range(settings: Settings, source: PostgresSource) -> tuple:
	min_created, max_created = source.fetch_order_created_bounds()
	start_date = settings.dim_date_start
	end_date = settings.dim_date_end
	if min_created is not None and min_created.date() < start_date:
		start_date = min_created.date()
	if max_created is not None and max_created.date() > end_date:
		end_date = max_created.date()
	return start_date, end_date


def run_dimensions(settings: Settings, source: PostgresSource, duck_conn) -> dict:
	ensure_warehouse_schema(duck_conn, settings)
	loaded_at = datetime.now(timezone.utc)

	start_date, end_date = _determine_date_range(settings, source)
	dim_date_inserted = load_dim_date(duck_conn, start_date, end_date)

	instrument_result = load_dim_instrument(
		duck_conn,
		source.fetch_instruments(),
		loaded_at,
	)

	account_result = load_dim_account(
		duck_conn,
		source.fetch_accounts(),
		as_of_date=loaded_at.date(),
		loaded_at=loaded_at,
	)

	return {
		"dim_date_inserted": dim_date_inserted,
		"dim_instrument": instrument_result,
		"dim_account": account_result,
	}


def run_facts(settings: Settings, source: PostgresSource, duck_conn) -> dict:
	ensure_warehouse_schema(duck_conn, settings)
	loaded_at = datetime.now(timezone.utc)

	watermark = read_watermark(settings)
	source_orders = source.fetch_orders_since(watermark)

	if not source_orders:
		return {
			"watermark_before": None if watermark is None else watermark.isoformat(),
			"watermark_after": None if watermark is None else watermark.isoformat(),
			"source_rows": 0,
			"inserted": 0,
			"skipped_duplicate": 0,
			"dead_lettered": 0,
		}

	dead_letter_store = DeadLetterStore(settings.dead_letter_path)

	fact_result = load_fact_trades(
		duck_conn,
		source_orders=source_orders,
		batch_id=settings.batch_id,
		loaded_at=loaded_at,
		dead_letter_store=dead_letter_store,
		allowed_sides=settings.allowed_sides,
		allowed_statuses=settings.allowed_statuses,
	)

	watermark_after = max(row["created_at"] for row in source_orders)
	write_watermark(settings, watermark_after)

	return {
		"watermark_before": None if watermark is None else watermark.isoformat(),
		"watermark_after": watermark_after.isoformat(),
		"source_rows": len(source_orders),
		**fact_result,
	}


def run_all(settings: Settings) -> dict:
	with connect_postgres(settings) as pg_conn:
		source = PostgresSource(pg_conn)
		duck_conn = connect_duckdb(settings)
		try:
			dim_result = run_dimensions(settings, source, duck_conn)
			fact_result = run_facts(settings, source, duck_conn)
			return {
				"dimensions": dim_result,
				"facts": fact_result,
			}
		finally:
			duck_conn.close()


def _build_parser() -> argparse.ArgumentParser:
	parser = argparse.ArgumentParser(description="Sprint ETL pipeline")
	subparsers = parser.add_subparsers(dest="command", required=True)

	subparsers.add_parser("dimensions", help="Load only dimensions")
	subparsers.add_parser("facts", help="Load only fact_trades incrementally")
	subparsers.add_parser("run", help="Run dimensions then facts")

	return parser


def main() -> None:
	args = _build_parser().parse_args()
	settings = Settings.from_env()

	with connect_postgres(settings) as pg_conn:
		source = PostgresSource(pg_conn)
		duck_conn = connect_duckdb(settings)
		try:
			if args.command == "dimensions":
				result = run_dimensions(settings, source, duck_conn)
			elif args.command == "facts":
				result = run_facts(settings, source, duck_conn)
			else:
				result = {
					"dimensions": run_dimensions(settings, source, duck_conn),
					"facts": run_facts(settings, source, duck_conn),
				}
		finally:
			duck_conn.close()

	print(result)


if __name__ == "__main__":
	main()

