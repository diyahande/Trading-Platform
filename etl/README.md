# Sprint 7 ETL Pipeline

This ETL loads the analytical star schema in DuckDB from Postgres operational tables.

It satisfies the Sprint acceptance criteria:

- Incremental load driven by watermark on `orders.created_at`
- Dimension load order: `dim_date` -> `dim_instrument` -> `dim_account`
- Fact load includes all order outcomes (`NEW`, `FILLED`, `REJECTED`, `CANCELLED`)
- Idempotent fact loading by natural key (`source_order_id`)
- Invalid rows are dead-lettered with reason and `batch_id`

## Setup

From repository root:

```powershell
cd etl
python -m venv .venv
.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -e ".[dev]"
```

The ETL reads Postgres settings from environment variables. If not exported, it auto-loads values from `database/.env`.

Required variables:

- `POSTGRES_DB`
- `PGHOST`
- `PGPORT`
- `PGUSER`
- `PGPASSWORD`

Optional variables:

- `ANALYTICS_DUCKDB_PATH` (default: `etl/analytics.duckdb`)
- `ANALYTICS_SCHEMA_PATH` (default: `analytics/contracts/analytics_schema.sql`)
- `ETL_WATERMARK_PATH` (default: `etl/.state/fact_trades.watermark`)
- `ETL_DEAD_LETTER_PATH` (default: `etl/dead_letter/fact_trades.jsonl`)
- `ETL_BATCH_ID` (default: generated UTC batch id)

## Pipeline Commands (run any stage independently)

1. Load dimensions only

```powershell
python -m analytics_etl.pipeline dimensions
```

2. Load facts incrementally only

```powershell
python -m analytics_etl.pipeline facts
```

3. Full run (dimensions, then facts)

```powershell
python -m analytics_etl.pipeline run
```

## How Incremental + Idempotent Works

- Last successful watermark is read from `etl/.state/fact_trades.watermark`
- ETL extracts only `orders.created_at > watermark`
- `source_order_id` in `fact_trades` is unique and used as natural key
- Existing `source_order_id` rows are skipped (no double count)
- Watermark advances to max extracted `created_at` at end of a successful run

## Dead-Letter Behavior

Invalid rows are written to JSONL dead-letter file, not dropped.

Each dead-letter record includes:

- `batch_id`
- `source_order_id`
- `reason`
- `failed_at`
- original source `payload`

## Data Quality Checks Before FACT_TRADES Insert

- Resolved `account_key` from `dim_account` for order date
- Resolved `instrument_key` from `dim_instrument`
- Resolved `date_key` from `dim_date`
- `quantity > 0` and integer
- `price > 0`
- `side` in `BUY`, `SELL`
- `status` in `NEW`, `FILLED`, `REJECTED`, `CANCELLED`
- `trade_value` recomputed from quantity and price basis

No placeholder dimension rows are created for unresolved keys.

## Running Tests

From `etl/`:

```powershell
pytest
```

The suite covers:

- incremental load populates `FACT_TRADES`
- second load with no new data adds no rows
- transform handles nulls and type mismatches
- invalid row dead-lettered while load continues

