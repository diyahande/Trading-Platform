import logging

# Logger used throughout the ETL pipeline
logger = logging.getLogger("analytics_etl")

class FauxnanceError(Exception):
    pass

#429 Too Many Requests
class RateLimited(FauxnanceError):
    def __init__(self, symbol, retry_after=None):
        self.symbol = symbol
        self.retry_after = retry_after

        message = f"rate limited fetching {symbol}"

        if retry_after:
            message += f"; Retry-After={retry_after}s"

        super().__init__(message)


#HTTP 4xx series
class ClientRequestError(FauxnanceError):
    def __init__(self, symbol, status_code, message=""):
        self.symbol = symbol
        self.status_code = status_code
        self.message = message

        super().__init__(
            f"HTTP {status_code} fetching {symbol}: {message}"
        )

#HTTP 5xx series
class ServerError(FauxnanceError):
    def __init__(self, symbol, status_code, message=""):
        self.symbol = symbol
        self.status_code = status_code
        self.message = message

        super().__init__(
            f"HTTP {status_code} from Fauxnance for {symbol}: {message}"
        )

#Network Errors
class TransientNetworkError(FauxnanceError):
    def __init__(self, symbol, attempts):
        self.symbol = symbol
        self.attempts = attempts

        super().__init__(
            f"network failure fetching "
            f"{symbol} after {attempts} attempts"
        )

#Data Structure and Schema Mismatch 
class MalformedPayload(FauxnanceError):
    def __init__(self, symbol, reason):
        self.symbol = symbol
        self.reason = reason

        super().__init__(
            f"malformed payload for "
            f"{symbol}: {reason}"
        )