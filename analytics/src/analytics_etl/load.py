import json
from pathlib import Path
from datetime import datetime

import duckdb

#paths
PROJECT_ROOT = Path(
    __file__
).resolve().parents[2]

SCHEMA_FILE = (
    PROJECT_ROOT
    / "contracts"
    / "analytics_schema.sql"
)

DEFAULT_DB = (
    PROJECT_ROOT
    / "analytics.duckdb"
)

DEFAULT_CACHE_DIR = (
    PROJECT_ROOT
    / ".cache"
)

#create analytical store
def create_analytical_store(
    db_path=DEFAULT_DB,
    schema_file=SCHEMA_FILE
):
    """
    Create or open DuckDB and execute analytics_schema.sql.
    """

    db_path = Path(
        db_path
    )

    schema_file = Path(
        schema_file
    )

    if not schema_file.exists():

        raise FileNotFoundError(
            f"Schema file not found: {schema_file}"
        )

    conn = duckdb.connect(
        str(db_path)
    )

    schema_sql = schema_file.read_text(
        encoding="utf-8"
    )

    conn.execute(
        schema_sql
    )

    conn.commit()

    print(
        f"Analytical store ready: {db_path}"
    )

    return conn

#get instrument key
def get_instrument_key(
    conn,
    symbol
):
    """
    Return existing instrument key or generate a new one.
    """

    result = conn.execute(
        """
        SELECT instrument_key
        FROM dim_instrument
        WHERE symbol = ?
        """,
        [symbol]
    ).fetchone()

    if result:

        return result[0]

    result = conn.execute(
        """
        SELECT COALESCE(
            MAX(instrument_key),
            0
        ) + 1
        FROM dim_instrument
        """
    ).fetchone()

    return result[0]

#load instrument
def load_instrument(
    conn,
    instrument_response
):
    """
    Load instrument information directly into DIM_INSTRUMENT.

    Instrument data does NOT pass through transform.py.
    """

    data = instrument_response[
        "data"
    ]

    symbol = data["symbol"]
    name = data["name"]
    asset_class = data["type"]
    exchange = data.get("exchange")
    currency = data["currency"]
    tradable = data["active"]

    instrument_key = get_instrument_key(
        conn,
        symbol
    )

    loaded_at = datetime.now()

    existing = conn.execute(
        """
        SELECT instrument_key
        FROM dim_instrument
        WHERE symbol = ?
        """,
        [symbol]
    ).fetchone()

    #update existing instrument
    if existing:

        conn.execute(
            """
            UPDATE dim_instrument
            SET
                name = ?,
                asset_class = ?,
                currency = ?,
                exchange = ?,
                tradable = ?,
                loaded_at = ?
            WHERE symbol = ?
            """,
            [
                name,
                asset_class,
                currency,
                exchange,
                tradable,
                loaded_at,
                symbol
            ]
        )

        print(
            f"Updated instrument: {symbol}"
        )

    #insert new instrument
    else:

        conn.execute(
            """
            INSERT INTO dim_instrument (
                instrument_key,
                symbol,
                name,
                asset_class,
                currency,
                exchange,
                tradable,
                loaded_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                instrument_key,
                symbol,
                name,
                asset_class,
                currency,
                exchange,
                tradable,
                loaded_at
            ]
        )

        print(
            f"Inserted instrument: {symbol}"
        )

    return instrument_key

#get next candle key
def get_next_candle_key(
    conn
):
    """
    Generate the next surrogate candle key.
    """

    result = conn.execute(
        """
        SELECT COALESCE(
            MAX(candle_key),
            0
        ) + 1
        FROM fact_candles
        """
    ).fetchone()

    return result[0]

#load transform candles
def load_transformed_candles(
    conn,
    transformed_candles,
    instrument_key
):
    """
    Load transformed candle DataFrame into FACT_CANDLES.

    Expected DataFrame columns:

        date
        open
        high
        low
        close
        adjclose
        volume
        synthetic
        daily_return

    price_range and price_range_pct are calculated here
    because the current transform.py does not produce them.
    """

    inserted = 0
    skipped = 0

    loaded_at = datetime.now()

    #empty dataframe
    if transformed_candles.empty:

        print(
            "No transformed candles to load."
        )

        return 0, 0

    #iterate through transformed candles
    for _, candle in transformed_candles.iterrows():

        #read transformed values
        candle_date = candle["date"]

        open_price = candle["open"]

        high_price = candle["high"]

        low_price = candle["low"]

        close_price = candle["close"]

        adjclose_price = candle["adjclose"]

        volume = candle["volume"]

        synthetic = candle["synthetic"]

        daily_return = candle["daily_return"]

        # Convert Pandas NaN to Python None

        if daily_return is not None:

            try:

                if daily_return != daily_return:

                    daily_return = None

            except Exception:

                pass

        #calculate price range
        price_range = (
            high_price
            - low_price
        )

        #calculate price range percentage
        if low_price != 0:

            price_range_pct = (
                price_range
                / low_price
            )

        else:

            price_range_pct = None

        #convert date to python date
        if hasattr(
            candle_date,
            "date"
        ):

            candle_date = (
                candle_date.date()
            )

        #check duplicaet
        existing = conn.execute(
            """
            SELECT candle_key
            FROM fact_candles
            WHERE instrument_key = ?
              AND candle_date = ?
            """,
            [
                instrument_key,
                candle_date
            ]
        ).fetchone()

        if existing:

            skipped += 1

            continue

        #generate candle key
        candle_key = get_next_candle_key(
            conn
        )

        #insert candle
        conn.execute(
            """
            INSERT INTO fact_candles (
                candle_key,
                instrument_key,
                candle_date,
                open_price,
                high_price,
                low_price,
                close_price,
                adjclose_price,
                volume,
                synthetic,
                daily_return,
                price_range,
                price_range_pct,
                loaded_at
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?
            )
            """,
            [
                candle_key,
                instrument_key,
                candle_date,
                open_price,
                high_price,
                low_price,
                close_price,
                adjclose_price,
                volume,
                synthetic,
                daily_return,
                price_range,
                price_range_pct,
                loaded_at
            ]
        )

        inserted += 1

    return inserted, skipped

#load extracted and transformed data
def load_from_data(
    instrument_response,
    transformed_candles,
    db_path=DEFAULT_DB
):
    """
    Load instrument and transformed candle data.

    Instrument:
        API/cache -> DIM_INSTRUMENT

    Candles:
        API/cache -> transform.py -> FACT_CANDLES
    """

    conn = create_analytical_store(
        db_path
    )

    try:

        #1. instruments
        instrument_key = load_instrument(
            conn,
            instrument_response
        )

        #2. Transformed candles
        inserted, skipped = (
            load_transformed_candles(
                conn,
                transformed_candles,
                instrument_key
            )
        )

        #3. Commit
        conn.commit()

        symbol = (
            instrument_response[
                "data"
            ][
                "symbol"
            ]
        )

        print(
            f"Loaded {symbol}: "
            f"{inserted} candles inserted, "
            f"{skipped} candles skipped."
        )

        return {
            "symbol": symbol,
            "instrument_key": instrument_key,
            "inserted": inserted,
            "skipped": skipped
        }

    except Exception:

        #safety rollback
        try:

            conn.rollback()

        except Exception:

            pass

        raise

    finally:

        conn.close()

#load from combined cache
def load_from_cache(
    cache_file,
    db_path=DEFAULT_DB
):
    """
    Load one combined cache JSON.
    Structure:
        {
            "instrument": {...},
            "candles": {...}
        }

    Instrument is loaded directly.
    Candles are transformed first.
    """

    cache_file = Path(
        cache_file
    )

    if not cache_file.exists():

        raise FileNotFoundError(
            f"Cache file not found: {cache_file}"
        )

    #read cache
    with open(
        cache_file,
        "r",
        encoding="utf-8"
    ) as file:

        cached_data = json.load(
            file
        )

    #import transform locally
    from analytics_etl.transform import (
        transform
    )

    #extract instrument
    instrument_response = (
        cached_data[
            "instrument"
        ]
    )

    #extract candles
    candle_response = (
        cached_data[
            "candles"
        ]
    )

    #transform only candles
    transformed_candles = transform(
        candle_response
    )

    #load
    return load_from_data(
        instrument_response,
        transformed_candles,
        db_path
    )

#load all cache files
def load_all_cache(
    cache_dir=DEFAULT_CACHE_DIR,
    db_path=DEFAULT_DB
):
    """
    Load every combined JSON cache file.
    """

    cache_dir = Path(
        cache_dir
    )

    if not cache_dir.exists():

        raise FileNotFoundError(
            f"Cache directory not found: {cache_dir}"
        )

    cache_files = sorted(
        cache_dir.glob("*.json")
    )

    if not cache_files:

        print(
            "No JSON cache files found."
        )

        return

    print(
        f"Found {len(cache_files)} cache file(s)."
    )

    for cache_file in cache_files:

        print(
            f"\nLoading: {cache_file.name}"
        )

        load_from_cache(
            cache_file,
            db_path
        )

#show loaded data
def show_loaded_data(
    db_path=DEFAULT_DB
):
    """
    Display a summary of loaded data.
    """

    conn = duckdb.connect(
        str(db_path)
    )

    try:
        #instruments
        print(
            "\nDIM_INSTRUMENT"
        )

        print(
            "----------------"
        )

        instruments = conn.execute(
            """
            SELECT
                instrument_key,
                symbol,
                name,
                asset_class,
                exchange,
                currency,
                tradable
            FROM dim_instrument
            ORDER BY instrument_key
            """
        ).fetchall()

        for row in instruments:

            print(row)

        #candle count
        print(
            "\nFACT_CANDLES"
        )

        print(
            "----------------"
        )

        candle_count = conn.execute(
            """
            SELECT COUNT(*)
            FROM fact_candles
            """
        ).fetchone()[0]

        print(
            f"Total candles: {candle_count}"
        )

        #candles by instrument
        print(
            "\nCandles by instrument:"
        )

        results = conn.execute(
            """
            SELECT
                i.symbol,
                COUNT(*) AS candle_count
            FROM fact_candles c
            JOIN dim_instrument i
                ON c.instrument_key =
                   i.instrument_key
            GROUP BY i.symbol
            ORDER BY i.symbol
            """
        ).fetchall()

        for row in results:

            print(row)

        #sample candle data
        print(
            "\nSample candle records:"
        )

        candles = conn.execute(
            """
            SELECT
                i.symbol,
                c.candle_date,
                c.open_price,
                c.high_price,
                c.low_price,
                c.close_price,
                c.adjclose_price,
                c.volume,
                c.synthetic,
                c.daily_return,
                c.price_range,
                c.price_range_pct
            FROM fact_candles c
            JOIN dim_instrument i
                ON c.instrument_key =
                   i.instrument_key
            ORDER BY c.candle_date
            LIMIT 10
            """
        ).fetchall()

        for row in candles:

            print(row)

    finally:

        conn.close()

#main
if __name__ == "__main__":

    load_all_cache()

    show_loaded_data()