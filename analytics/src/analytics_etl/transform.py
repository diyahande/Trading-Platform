import pandas as pd

#required candle columns
REQUIRED_COLUMNS = [
    "date",
    "open",
    "high",
    "low",
    "close",
    "adjclose",
    "volume",
    "synthetic",
]

#numeric columns
NUMERIC_COLUMNS = [
    "open",
    "high",
    "low",
    "close",
    "adjclose",
    "volume",
]

#Validate required columns
def _validate_required_columns(df):
    """
    Check that all required candle fields are present.
    """

    missing_columns = [
        column
        for column in REQUIRED_COLUMNS
        if column not in df.columns
    ]
    if missing_columns:
        raise ValueError(
            f"Missing required columns: {missing_columns}"
        )

    return df

#Validate Numeric Columns
def _validate_numeric_columns(df):
    """
    Convert numeric candle fields to numeric types.

    Values that cannot be converted become NaN.
    """

    for column in NUMERIC_COLUMNS:

        df[column] = pd.to_numeric(
            df[column],
            errors="coerce"
        )

    return df

#Validate Dates
def _validate_dates(df):
    """
    Convert dates to datetime.

    Invalid dates become NaT.
    """

    df["date"] = pd.to_datetime(
        df["date"],
        format="%Y-%m-%d",
        errors="coerce"
    )

    return df

#Remove missing values
def _remove_missing_values(df):
    """
    Remove candles with missing essential values.
    """

    return df.dropna(
        subset=[
            "date",
            "open",
            "high",
            "low",
            "close",
            "adjclose",
            "volume",
        ]
    )

#remove invalid OHLC
def _remove_invalid_ohlc(df):
    """
    Remove candles where high is below low.
    """

    return df[
        df["high"] >= df["low"]
    ]

# Remove negative volume
def _remove_negative_volume(df):
    """
    Remove candles with negative volume.
    """

    return df[
        df["volume"] >= 0
    ]

#remove duplicate dates
def _remove_duplicate_dates(df):
    """
    Keep the first candle for each trading date.
    """

    return df.drop_duplicates(
        subset=["date"],
        keep="first"
    )

#remove synthetic candles
def _remove_synthetic_candles(df):
    """
    Remove candles marked as synthetic.
    """

    return df[
        df["synthetic"] == False
    ]

#sort by date
def _sort_by_date(df):
    """
    Sort candles chronologically.
    """

    return df.sort_values(
        "date"
    )

#derive daily return
def _derive_daily_return(df):
    """
    Calculate daily percentage return based on closing prices.

    The first candle has no previous closing price, so its
    daily_return is left as None/NULL.
    """

    df["daily_return"] = (
        df["close"].pct_change()
    )

    return df

#main transform function
def transform(raw_data):
    """
    Transform a raw Fauxnance CandlesResponse into
    a clean analytical DataFrame.

    Only candle data is transformed.

    Instrument data is NOT handled here.
    """
    #extract candles
    candles = raw_data[
        "data"
    ][
        "candles"
    ]

    df = pd.DataFrame(
        candles
    )

    #empty dataset
    if df.empty:

        return pd.DataFrame(
            columns=REQUIRED_COLUMNS + [
                "daily_return"
            ]
        )

    #validation and cleaning
    df = _validate_required_columns(df)

    df = _validate_numeric_columns(df)

    df = _validate_dates(df)

    df = _remove_missing_values(df)

    df = _remove_invalid_ohlc(df)

    df = _remove_negative_volume(df)

    df = _remove_duplicate_dates(df)

    df = _remove_synthetic_candles(df)

    #sort chronologically
    df = _sort_by_date(df)

    #calculate daily return
    df = _derive_daily_return(df)

    # Replace NaN with None
    # First daily return is naturally NaN because there is
    # no previous closing price.

    df["daily_return"] = df[
        "daily_return"
    ].where(
        pd.notna(
            df["daily_return"]
        ),
        None
    )

    #reset index
    return df.reset_index(
        drop=True
    )