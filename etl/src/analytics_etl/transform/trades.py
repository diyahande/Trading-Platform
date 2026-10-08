from __future__ import annotations

from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP


TWOPLACES = Decimal("0.01")


def _to_decimal(raw_value, field_name: str, errors: list[str], allow_none: bool = False) -> Decimal | None:
    if raw_value is None:
        if allow_none:
            return None
        errors.append(f"{field_name} is null")
        return None
    try:
        value = Decimal(str(raw_value))
    except (InvalidOperation, TypeError, ValueError):
        errors.append(f"{field_name} is not numeric")
        return None
    return value


def _to_utc(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def transform_order_row(raw: dict, allowed_sides: set[str], allowed_statuses: set[str]) -> tuple[dict | None, list[str]]:
    """Normalize one source order row into a FACT_TRADES candidate."""
    errors: list[str] = []

    source_order_id = raw.get("order_id")
    source_account_id = raw.get("account_id")
    source_instrument_id = raw.get("instrument_id")
    symbol = raw.get("symbol")
    side = raw.get("side")
    status = raw.get("status")
    created_at = raw.get("created_at")

    if source_order_id is None:
        errors.append("order_id is null")
    if source_account_id is None:
        errors.append("account_id is null")
    if source_instrument_id is None:
        errors.append("instrument_id is null")
    if not symbol:
        errors.append("symbol is null or empty")
    if side not in allowed_sides:
        errors.append(f"side must be one of {sorted(allowed_sides)}")
    if status not in allowed_statuses:
        errors.append(f"status must be one of {sorted(allowed_statuses)}")
    if not isinstance(created_at, datetime):
        errors.append("created_at is not a datetime")

    quantity_decimal = _to_decimal(raw.get("quantity"), "quantity", errors)
    price_decimal = _to_decimal(raw.get("price"), "price", errors)
    executed_price_decimal = _to_decimal(
        raw.get("executed_price"),
        "executed_price",
        errors,
        allow_none=True,
    )

    if quantity_decimal is not None:
        if quantity_decimal <= 0:
            errors.append("quantity must be > 0")
        if quantity_decimal != quantity_decimal.to_integral_value():
            errors.append("quantity must be an integer")

    if price_decimal is not None and price_decimal <= 0:
        errors.append("price must be > 0")

    if executed_price_decimal is not None and executed_price_decimal <= 0:
        errors.append("executed_price must be > 0 when present")

    if status == "FILLED" and executed_price_decimal is None:
        errors.append("executed_price is required for FILLED status")

    if errors:
        return None, errors

    created_at_utc = _to_utc(created_at)
    price_rounded = price_decimal.quantize(TWOPLACES, rounding=ROUND_HALF_UP)
    executed_price_rounded = (
        executed_price_decimal.quantize(TWOPLACES, rounding=ROUND_HALF_UP)
        if executed_price_decimal is not None
        else None
    )

    quantity = int(quantity_decimal)
    basis_price = executed_price_rounded if executed_price_rounded is not None else price_rounded
    trade_value = (basis_price * Decimal(quantity)).quantize(TWOPLACES, rounding=ROUND_HALF_UP)

    candidate = {
        "source_order_id": str(source_order_id),
        "source_account_id": int(source_account_id),
        "source_instrument_id": int(source_instrument_id),
        "symbol": str(symbol),
        "side": str(side),
        "status": str(status),
        "quantity": quantity,
        "price": price_rounded,
        "executed_price": executed_price_rounded,
        "trade_value": trade_value,
        "created_at": created_at_utc,
        "date_key": int(created_at_utc.strftime("%Y%m%d")),
    }
    return candidate, []
