```markdown name=README.md
# Sprint 04 Analytics ETL

Market data analytics ETL pipeline that:

1. Extracts instrument metadata and candle data from the Fauxnance API
2. Caches raw responses locally
3. Transforms candle data into a clean analytical format
4. Loads data into a DuckDB analytical store
5. Optionally displays loaded data for verification

## Project Structure

```text
analytics/
├── pyproject.toml
├── src/
│   └── analytics_etl/
│       ├── extract.py
│       ├── transform.py
│       ├── load.py
│       └── pipeline.py
└── .cache/
```

## Requirements

- Python 3.10 or newer
- DuckDB
- A Fauxnance API key

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/Neueda-Learning/chennai-capstone-SE5-team4.git
cd chennai-capstone-SE5-team4/analytics
```

### 2. Create and activate a virtual environment

#### macOS / Linux

```bash
python3 -m venv .venv
source .venv/bin/activate
```

#### Windows PowerShell

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
```

### 3. Install dependencies

```bash
pip install -e .
```

For development dependencies:

```bash
pip install -e ".[dev]"
```

### 4. Configure environment variables

Copy the repo-root `.env.example` to `.env` and set `FAUXNANCE_API_KEY` (and the other values it lists). All services, scripts and tests read this one root file.

```env
FAUXNANCE_API_KEY=your_api_key_here
```

## Database Setup

The pipeline expects a DuckDB database and schema file.

### Expected files

- `analytics.duckdb` in the repository root
- `contracts/analytics_schema.sql` in the repository root

The loader will create/open the DuckDB file automatically, but the schema file must exist.

## How It Works

### Extract
- Fetches instrument metadata and candle data from the Fauxnance API
- Saves both responses together into a cache file under `.cache/`

### Transform
- Validates required candle fields
- Converts numeric and date columns
- Removes invalid, duplicate, missing, and synthetic candles
- Sorts data chronologically
- Derives `daily_return`

### Load
- Loads instrument data directly into `dim_instrument`
- Loads transformed candle data into `fact_candles`
- Avoids duplicate candle inserts
- Calculates `price_range` and `price_range_pct`

## Running the Pipeline

### Run as a module

From the `analytics/` directory:

```bash
python -m analytics_etl.pipeline
```

### Run using the console script

If installed with `pip install -e .`, you can also run:

```bash
analytics-run
```

## Example Usage

```python
from analytics_etl.pipeline import run_pipeline

run_pipeline(
    "ICICIBANK.NS",
    "2026-07-01",
    "2026-07-10"
)
```

## Cached Data

Cached API responses are stored in:

```text
analytics/.cache/
```

Cache files are named like:

```text
SYMBOL_STARTDATE_ENDDATE.json
```

Example:

```text
ICICIBANK.NS_2026-07-01_2026-07-10.json
```

## Output Tables

### `dim_instrument`
Stores instrument metadata such as:

- instrument_key
- symbol
- name
- asset_class
- exchange
- currency
- tradable
- loaded_at

### `fact_candles`
Stores cleaned candle data such as:

- candle_key
- instrument_key
- candle_date
- open_price
- high_price
- low_price
- close_price
- adjclose_price
- volume
- synthetic
- daily_return
- price_range
- price_range_pct
- loaded_at

## Troubleshooting

### `FAUXNANCE_API_KEY is not set`
Make sure the repo-root `.env` file contains a valid API key.

### `Schema file not found`
Check that this file exists:

```text
contracts/analytics_schema.sql
```

relative to the repository root.

### No cache files found
If no `.json` files exist in `.cache/`, run the extraction step or the full pipeline first.

## Development Notes

- `transform.py` only processes candle data
- Instrument data bypasses transformation and is loaded directly
- Duplicate candles are skipped during loading
- Synthetic candles are removed during transformation
