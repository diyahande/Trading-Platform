from analytics_etl.extract import extract_from_api
from analytics_etl.transform import transform
from analytics_etl.load import load_from_data


def run_pipeline(symbol, start_date, end_date):

    # Extracted data comes from cache
    raw_data = extract_from_api(
        symbol,
        start_date,
        end_date
    )

    # Transform only candles
    transformed_candles = transform(
        raw_data["candles"]
    )

    # Instrument goes directly to load.
    # Candles go through transform first.
    return load_from_data(
        raw_data["instrument"],
        transformed_candles
    )


if __name__ == "__main__":

    symbols = ["ICICIBANK.NS", "HDFCBANK.NS", "AXISBANK.NS"]
    
    for symbol in symbols :
        run_pipeline(
            symbol,
            "2026-07-01",
            "2026-07-10"
        )