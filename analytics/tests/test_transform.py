import json
from pathlib import Path

import pandas as pd
#import pytest

from analytics_etl.transform import transform

#fixture location
FIXTURES_DIR = (
    Path(__file__).resolve().parents[1]
    / "fixtures"
)

def load_fixture(filename):
    """
    Load a canned Fauxnance response from fixtures/.
    No network call is made.
    """
    
    fixture_path = FIXTURES_DIR / filename
    with open(
        fixture_path,
        "r",
        encoding="utf-8"
    ) as file:
        return json.load(file)

#1. Valid Input
def test_valid_input_transforms_successfully():
    raw_data = load_fixture(
        "candles-infy-ns-2026-07.json"
    )
    result = transform(raw_data)
    assert isinstance(result, pd.DataFrame)
    assert not result.empty

    # Required transformed columns
    expected_columns = {
        "date",
        "open",
        "high",
        "low",
        "close",
        "adjclose",
        "volume",
        "synthetic",
        "daily_return",
    }

    assert expected_columns.issubset(
        result.columns
    )

    # Dates should be chronological
    assert result["date"].is_monotonic_increasing

    # No invalid OHLC relationship
    assert (
        result["high"] >= result["low"]
    ).all()

    # No negative volume
    assert (
        result["volume"] >= 0
    ).all()

#2.Required field missing
def test_rejects_a_missing_close_value():
    raw_data = load_fixture(
        "candles-malformed.json"
    )
    result = transform(raw_data)
    invalid_date = pd.Timestamp(
        "2026-07-02"
    )
    assert invalid_date not in (
        result["date"].values
    )

#3.Non Numeric Value
def test_rejects_a_non_numeric_price():

    raw_data = load_fixture(
        "candles-malformed.json"
    )

    result = transform(raw_data)

    # The malformed fixture has "n/a" as a numeric value
    # on 2026-07-06.
    invalid_date = pd.Timestamp(
        "2026-07-06"
    )

    assert invalid_date not in (
        result["date"].values
    )

#4.Invalid Date
def test_rejects_a_non_iso_date():

    raw_data = load_fixture(
        "candles-malformed.json"
    )

    result = transform(raw_data)

    # The malformed fixture contains:
    # 09/07/2026
    # Only YYYY-MM-DD is accepted.
    invalid_date = "09/07/2026"

    assert invalid_date not in (
        result["date"].astype(str).values
    )

#5.High Below Low
def test_rejects_a_high_below_a_low():

    raw_data = load_fixture(
        "candles-malformed.json"
    )

    result = transform(raw_data)

    # The malformed fixture contains a candle on
    # 2026-07-07 where high < low.

    invalid_date = pd.Timestamp(
        "2026-07-07"
    )

    assert invalid_date not in (
        result["date"].values
    )

#6. Negative Volume
def test_rejects_negative_volume():

    raw_data = load_fixture(
        "candles-malformed.json"
    )

    result = transform(raw_data)

    # The malformed fixture contains negative
    # volume on 2026-07-08.

    invalid_date = pd.Timestamp(
        "2026-07-08"
    )

    assert invalid_date not in (
        result["date"].values
    )

#7. Duplicate Date
def test_removes_duplicate_dates():

    raw_data = load_fixture(
        "candles-malformed.json"
    )

    result = transform(raw_data)

    # 2026-07-01 appears twice in the malformed
    # fixture.

    date_count = (
        result["date"]
        == pd.Timestamp("2026-07-01")
    ).sum()

    assert date_count == 1

#8.Synthetic Candle
def test_removes_synthetic_candles():

    raw_data = load_fixture(
        "candles-infy-ns-2026-07.json"
    )

    result = transform(raw_data)

    # 2026-07-08 is marked synthetic=True
    synthetic_date = pd.Timestamp(
        "2026-07-08"
    )

    assert synthetic_date not in (
        result["date"].values
    )

#9.Empty Dataset
def test_empty_dataset_is_handled_without_error():

    raw_data = {
        "data": {
            "symbol": "INFY.NS",
            "interval": "1d",
            "currency": "INR",
            "candles": []
        },
        "meta": {
            "asOf": "2026-07-11T06:00:00Z",
            "disclaimer": "Educational data",
            "symbol": "INFY.NS",
            "source": "mixed"
        }
    }

    result = transform(raw_data)
    assert isinstance(
        result,
        pd.DataFrame
    )
    assert result.empty