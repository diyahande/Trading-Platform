-- 1. All open orders for an account, newest first

-- Useful for the order dashboard.

SELECT
    o.order_id,
    o.account_reference,
    i.symbol,
    i.display_name,
    o.side,
    o.quantity,
    o.price,
    o.status,
    o.created_at
FROM orders o
JOIN instruments i
    ON i.instrument_id = o.instrument_id
WHERE o.account_id = :account_id
  AND o.status = 'NEW'
ORDER BY o.created_at DESC;

-- Index:
--  Account's open orders filtered by status and already sorted newest first.
CREATE INDEX idx_orders_account_status_created
    ON orders(account_id, status, created_at DESC);


-- 2. Last 50 orders for an account

-- Useful for the order history screen.

SELECT
    o.order_id,
    o.account_reference,
    i.symbol,
    o.side,
    o.quantity,
    o.price,
    o.status,
    o.created_at
FROM orders o
JOIN instruments i
    ON i.instrument_id = o.instrument_id
WHERE o.account_id = :account_id
ORDER BY o.created_at DESC
LIMIT 50;

-- Index:

CREATE INDEX idx_orders_account_created
    ON orders(account_id, created_at DESC);

-- 3. Current portfolio holdings for an account

-- Useful for the portfolio page.

SELECT
    p.portfolio_id,
    i.symbol,
    i.display_name,
    i.asset_class,
    ph.quantity,
    ph.average_price
FROM portfolios p
JOIN portfolio_holdings ph
    ON ph.portfolio_id = p.portfolio_id
JOIN instruments i
    ON i.instrument_id = ph.instrument_id
WHERE p.account_id = :account_id
  AND ph.quantity > 0
ORDER BY i.symbol;


-- 4. Orders created since a timestamp
-- Incremental Extraction

SELECT
    o.order_id,
    o.account_id,
    o.account_reference,
    o.instrument_id,
    i.symbol,
    o.side,
    o.quantity,
    o.price,
    o.status,
    o.created_at
FROM orders o
JOIN instruments i
    ON i.instrument_id = o.instrument_id
WHERE o.created_at >= :since_timestamp
ORDER BY o.created_at ASC;

-- Index:

CREATE INDEX idx_orders_created_at
    ON orders(created_at);


-- 5. Resolve an account using customer-facing reference

-- Useful for support/auth/API requests.

SELECT
    account_id,
    user_id,
    account_reference,
    holder_name,
    currency,
    cash_balance,
    status,
    created_at
FROM accounts
WHERE account_reference = :account_reference;




-- 6. Filled orders with running cash committed + rank by instrument

-- It is useful for statements/analytics and demonstrates a window function.

WITH filled_orders AS (
    SELECT
        o.order_id,
        o.account_id,
        o.instrument_id,
        i.symbol,
        o.side,
        o.quantity,
        o.price,
        o.quantity * o.price AS order_value,
        o.created_at
    FROM orders o
    JOIN instruments i
        ON i.instrument_id = o.instrument_id
    WHERE o.account_id = :account_id
      AND o.status = 'FILLED'
)
SELECT
    order_id,
    symbol,
    side,
    quantity,
    price,
    order_value,

    SUM(order_value) OVER (
        ORDER BY created_at
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS running_cash_committed,

    RANK() OVER (
        PARTITION BY instrument_id
        ORDER BY order_value DESC
    ) AS value_rank_within_instrument,

    created_at
FROM filled_orders
ORDER BY created_at ASC;