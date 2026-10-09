import time
import requests

from analytics_etl.error_handling import (
    RateLimited,
    ClientRequestError,
    ServerError,
    TransientNetworkError,
    MalformedPayload,
    logger,
)


class FauxnanceClient:

    def __init__(
        self,
        api_key,
        base_url,
    ):
        self.api_key = api_key
        self.base_url = base_url.rstrip("/")

    # Common GET request handler
    def _get(self, url, symbol, params=None):
        """
        Perform a GET request with authentication.

        Handles:
        - Network errors / timeouts
        - 429 rate limits
        - Other 4xx client errors
        - 5xx server errors
        - Invalid JSON
        - Malformed JSON payloads
        """

        headers = {
            "X-Api-Key": self.api_key
        }

        max_attempts = 3

        for attempt in range(1, max_attempts + 1):

            try:
                response = requests.get(
                    url,
                    headers=headers,
                    params=params,
                    timeout=10,
                )

            except (
                requests.exceptions.ConnectionError,
                requests.exceptions.Timeout,
            ):

                if attempt == max_attempts:
                    logger.error(
                        "Network failure fetching %s "
                        "after %d attempts.",
                        symbol,
                        max_attempts,
                    )

                    raise TransientNetworkError(
                        symbol,
                        max_attempts,
                    )

                wait_time = 2 ** (attempt - 1)

                logger.warning(
                    "Network error fetching %s. "
                    "Attempt %d/%d. "
                    "Retrying in %d seconds.",
                    symbol,
                    attempt,
                    max_attempts,
                    wait_time,
                )

                time.sleep(wait_time)
                continue

            # 429 - rate limit
            if response.status_code == 429:

                retry_after = response.headers.get(
                    "Retry-After"
                )

                logger.error(
                    "Rate limited fetching %s; "
                    "Retry-After=%ss. "
                    "Stopping pipeline.",
                    symbol,
                    retry_after,
                )

                raise RateLimited(
                    symbol,
                    retry_after,
                )

            # Other 4xx errors
            if 400 <= response.status_code < 500:

                message = response.text

                logger.error(
                    "HTTP %s fetching %s: %s.",
                    response.status_code,
                    symbol,
                    message,
                )

                raise ClientRequestError(
                    symbol,
                    response.status_code,
                    message,
                )

            # 5xx server errors
            if 500 <= response.status_code < 600:

                message = response.text

                logger.error(
                    "Server error HTTP %s fetching %s: %s.",
                    response.status_code,
                    symbol,
                    message,
                )

                raise ServerError(
                    symbol,
                    response.status_code,
                    message,
                )

            # Any unexpected HTTP status
            response.raise_for_status()

            # Parse JSON
            try:
                raw_data = response.json()

            except ValueError as exc:

                logger.error(
                    "Invalid JSON received for %s.",
                    symbol,
                )

                raise MalformedPayload(
                    symbol,
                    "response is not valid JSON",
                ) from exc

            # Validate basic payload structure
            if not isinstance(raw_data, dict):

                raise MalformedPayload(
                    symbol,
                    "response is not a JSON object",
                )

            return raw_data

        raise TransientNetworkError(
            symbol,
            max_attempts,
        )

    # Get candles
    def get_candles(
        self,
        symbol,
        start_date,
        end_date,
    ):
        """
        Fetch daily candles from Fauxnance.

        Endpoint:
            GET /candles/{symbol}

        Returns:
            Raw JSON response unchanged.
        """

        url = (
            f"{self.base_url}"
            f"/candles/{symbol}"
        )

        params = {
            "start": start_date,
            "end": end_date,
            "interval": "1d",
        }

        logger.info(
            "Fetching candles for %s from Fauxnance API",
            symbol,
        )

        raw_data = self._get(
            url,
            symbol,
            params,
        )

        # Candle-specific payload validation
        if (
            "data" not in raw_data
            or not isinstance(raw_data["data"], dict)
            or "candles" not in raw_data["data"]
        ):
            raise MalformedPayload(
                symbol,
                "missing data.candles",
            )

        return raw_data

    # Get instrument information
    def get_instrument(self, symbol):
        """
        Fetch instrument details from Fauxnance.

        Endpoint:
            GET /symbols/{symbol}

        Returns:
            Raw JSON response unchanged.
        """

        url = (
            f"{self.base_url}"
            f"/symbols/{symbol}"
        )

        logger.info(
            "Fetching instrument information for %s",
            symbol,
        )

        return self._get(
            url,
            symbol,
        )