import json
import os
import time
from pathlib import Path

import requests
from dotenv import load_dotenv

from analytics_etl.error_handling import (
    RateLimited,
    ClientRequestError,
    TransientNetworkError,
    logger,
)

from analytics_etl.fauxnance_client import FauxnanceClient

load_dotenv()

CACHE_DIR = Path(".cache")
CACHE_DIR.mkdir(exist_ok=True)

#Return the cache file path for a symbol and date range
def _get_cache_path(symbol, start_date, end_date):
    safe_symbol = symbol.replace("/", "_")
    return (
        CACHE_DIR
        / f"{safe_symbol}_{start_date}_{end_date}.json"
    )

def _save_to_cache(
    symbol,
    start_date,
    end_date,
    instrument_data,
    candle_data,
):
    """
    Save instrument and candle API responses together
    in a single cache file.
    The API responses are stored unchanged.
    """
    cache_file = _get_cache_path(
        symbol,
        start_date,
        end_date,
    )
    combined_data = {
        "instrument": instrument_data,
        "candles": candle_data,
    }
    with open(
        cache_file,
        "w",
        encoding="utf-8",
    ) as file:

        json.dump(
            combined_data,
            file,
            indent=4,
        )
    logger.info(
        "Cached instrument and candle data for %s",
        symbol,
    )
    return cache_file

def _load_from_cache(
    symbol,
    start_date,
    end_date,
):
    """
    Read combined instrument and candle data
    from the cache.
    """
    cache_file = _get_cache_path(
        symbol,
        start_date,
        end_date,
    )
    if not cache_file.exists():
        return None
    logger.info(
        "Using cached data for %s",
        symbol,
    )
    with open(
        cache_file,
        "r",
        encoding="utf-8",
    ) as file:

        return json.load(file)


def extract_from_api(
    symbol,
    start_date,
    end_date,
):
    """
    Extract instrument metadata and candle data.
    Both API responses are stored together in one cache file.
    The API key is read only from FAUXNANCE_API_KEY.
    Returns:
        Dictionary containing both raw API responses.
    """
    cached_data = _load_from_cache(
        symbol,
        start_date,
        end_date,
    )
    if cached_data is not None:
        return cached_data

    #read API key from env
    api_key = os.getenv(
        "FAUXNANCE_API_KEY"
    )

    if not api_key:
        raise ValueError(
            "FAUXNANCE_API_KEY is not set"
        )

    base_url = os.getenv("FAUXNANCE_BASE_URL")

    if not base_url:
        raise ValueError(
            "FAUXNANCE_BASE_URL is not set"
        )

    #creating API client
    client = FauxnanceClient(
        api_key=api_key,
        base_url=base_url,
    )

    #get instrument info
    logger.info(
        "Fetching instrument information for %s",
        symbol,
    )
    instrument_data = (
        client.get_instrument(
            symbol
        )
    )
    #get candle info
    logger.info(
        "Fetching candles for %s",
        symbol,
    )
    candle_data = (
        client.get_candles(
            symbol,
            start_date,
            end_date,
        )
    )

    #saving both raw responses together
    _save_to_cache(
        symbol,
        start_date,
        end_date,
        instrument_data,
        candle_data,
    )

    #return combined raw data
    return {
        "instrument": instrument_data,
        "candles": candle_data,
    }

def extract_from_fixture(file_path):
    """
    Read a raw JSON fixture from disk.
    Used for testing and development.
    Args:
        file_path: Path to the JSON fixture.
    Returns:
        Raw JSON response unchanged.
    """

    fixture_file = Path(file_path)

    if not fixture_file.exists():
        raise FileNotFoundError(
            f"Fixture not found: {fixture_file}"
        )

    with open(
        fixture_file,
        "r",
        encoding="utf-8",
    ) as file:

        return json.load(file)
    

# data = extract_from_api(
#         "INFY.NS",
#         "2026-07-01",
#         "2026-07-10"
#     )

# print(data)