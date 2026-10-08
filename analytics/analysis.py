import duckdb
import pandas as pd
import matplotlib.pyplot as plt
from pathlib import Path


DB_PATH = Path("analytics.duckdb")
CHART_DIR = Path("charts")


SYMBOLS = [
    "HDFCBANK.NS",
    "ICICIBANK.NS",
    "AXISBANK.NS",
]


def get_candle_data():
    """
    Read candle analytics directly from DuckDB.
    """

    connection = duckdb.connect(str(DB_PATH))

    try:
        query = """
            SELECT
                i.symbol,
                i.name,
                i.currency,
                c.candle_date AS date,
                c.close_price,
                c.volume,
                c.daily_return,
                c.price_range,
                c.price_range_pct
            FROM fact_candles c
            JOIN dim_instrument i
                ON c.instrument_key = i.instrument_key
            WHERE i.symbol IN (?, ?, ?)
              AND c.synthetic = FALSE
            ORDER BY c.candle_date, i.symbol
        """

        return connection.execute(
            query,
            SYMBOLS
        ).df()

    finally:
        connection.close()


def plot_closing_price_comparison(df):
    """
    Chart 1:
    Compare closing price movement of the three banks.
    """

    plt.figure(figsize=(12, 6))

    for symbol in SYMBOLS:

        bank = df[df["symbol"] == symbol]

        if bank.empty:
            continue

        plt.plot(
            bank["date"],
            bank["close_price"],
            label=bank["name"].iloc[0]
        )

    plt.title(
        "Closing Price Movement of HDFC Bank, ICICI Bank and Axis Bank"
    )

    plt.xlabel("Trading Date")

    plt.ylabel("Closing Price (INR per share)")

    plt.legend()

    plt.xticks(rotation=45)

    plt.tight_layout()

    plt.savefig(
        CHART_DIR / "closing_price_comparison.png"
    )

    plt.close()


def plot_volume_comparison(df):
    """
    Chart 2:
    Compare daily trading volume.
    """

    plt.figure(figsize=(12, 6))

    for symbol in SYMBOLS:

        bank = df[df["symbol"] == symbol]

        if bank.empty:
            continue

        plt.plot(
            bank["date"],
            bank["volume"],
            label=bank["name"].iloc[0]
        )

    plt.title(
        "Daily Trading Volume of HDFC Bank, ICICI Bank and Axis Bank"
    )

    plt.xlabel("Trading Date")

    plt.ylabel("Trading Volume (Shares)")

    plt.legend()

    plt.xticks(rotation=45)

    plt.tight_layout()

    plt.savefig(
        CHART_DIR / "trading_volume_comparison.png"
    )

    plt.close()


def plot_volatility_comparison(df):
    """
    Chart 3:
    Compare average absolute daily returns.
    """

    volatility = (
        df.groupby(["symbol", "name"])["daily_return"]
        .apply(lambda values: values.abs().mean() * 100)
        .reset_index(name="average_absolute_daily_return")
    )

    plt.figure(figsize=(8, 5))

    plt.bar(
        volatility["name"],
        volatility["average_absolute_daily_return"]
    )

    plt.title(
        "Average Daily Price Volatility of HDFC Bank, ICICI Bank and Axis Bank"
    )

    plt.xlabel("Bank")

    plt.ylabel("Average Absolute Daily Return (%)")

    plt.xticks(rotation=30)

    plt.tight_layout()

    plt.savefig(
        CHART_DIR / "volatility_comparison.png"
    )

    plt.close()


def main():

    CHART_DIR.mkdir(
        exist_ok=True
    )

    df = get_candle_data()

    if df.empty:
        raise ValueError(
            "No candle data found for the selected symbols."
        )

    plot_closing_price_comparison(df)

    plot_volume_comparison(df)

    plot_volatility_comparison(df)

    print("Charts created successfully.")


if __name__ == "__main__":
    main()