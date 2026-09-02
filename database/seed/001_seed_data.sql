-- ============================================================
-- Sprint 3 Trade Database - Seed Data
--
-- Covers:
--   - ACTIVE, SUSPENDED and CLOSED accounts
--   - Account with almost no cash
--   - Several instruments
--   - Equity and non-equity instrument
--   - Delisted/non-tradable instrument
--   - NEW, FILLED, REJECTED and CANCELLED orders
--   - Filled orders and corresponding transactions
--   - Portfolio holdings reconciled against filled orders
--   - Timestamps spread across several months
-- ============================================================


-- ============================================================
-- 1. USERS
-- ============================================================

INSERT INTO users (
    name,
    email,
    phone_no,
    created_at
)
VALUES
(
    'Alice Johnson',
    'alice.johnson@example.com',
    '+1-555-0101',
    '2026-01-15 09:30:00+00'
),
(
    'Bob Smith',
    'bob.smith@example.com',
    '+1-555-0102',
    '2026-03-10 11:15:00+00'
),
(
    'Charlie Brown',
    'charlie.brown@example.com',
    '+1-555-0103',
    '2026-05-20 14:45:00+00'
);


-- ============================================================
-- 2. ACCOUNTS
-- ============================================================

INSERT INTO accounts (
    user_id,
    account_reference,
    holder_name,
    currency,
    cash_balance,
    status,
    created_at
)
VALUES
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'alice.johnson@example.com'
    ),
    'ACC-10001',
    'Alice Johnson',
    'USD',
    15000.0000,
    'ACTIVE',
    '2026-01-20 10:00:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'bob.smith@example.com'
    ),
    'ACC-10002',
    'Bob Smith',
    'USD',
    25.0000,
    'ACTIVE',
    '2026-03-15 12:00:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'charlie.brown@example.com'
    ),
    'ACC-10003',
    'Charlie Brown',
    'USD',
    5000.0000,
    'SUSPENDED',
    '2026-05-25 15:00:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'alice.johnson@example.com'
    ),
    'ACC-10004',
    'Alice Johnson',
    'USD',
    0.0000,
    'CLOSED',
    '2026-02-01 09:00:00+00'
);


-- ============================================================
-- 3. INSTRUMENTS
--
-- Replace symbols with the exact Fauxnance API symbols if
-- your repository uses different symbols.
-- ============================================================

INSERT INTO instruments (
    symbol,
    display_name,
    asset_class,
    quote_currency,
    is_tradable,
    created_at
)
VALUES
(
    'AAPL',
    'Apple Inc.',
    'EQUITY',
    'USD',
    TRUE,
    '2025-11-01 09:00:00+00'
),
(
    'MSFT',
    'Microsoft Corporation',
    'EQUITY',
    'USD',
    TRUE,
    '2025-12-01 09:00:00+00'
),
(
    'SPY',
    'SPDR S&P 500 ETF Trust',
    'ETF',
    'USD',
    TRUE,
    '2025-12-15 09:00:00+00'
),
(
    'OLDCO',
    'Old Company',
    'EQUITY',
    'USD',
    FALSE,
    '2025-10-01 09:00:00+00'
);


-- ============================================================
-- 4. PORTFOLIOS
--
-- One portfolio per account.
-- ============================================================

INSERT INTO portfolios (
    account_id,
    current_value,
    invested_value,
    updated_at
)
VALUES
(
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = 'ACC-10001'
    ),
    18500.0000,
    15000.0000,
    '2026-08-20 16:00:00+00'
),
(
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = 'ACC-10002'
    ),
    0.0000,
    0.0000,
    '2026-08-20 16:05:00+00'
),
(
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = 'ACC-10003'
    ),
    0.0000,
    0.0000,
    '2026-08-20 16:10:00+00'
),
(
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = 'ACC-10004'
    ),
    1000.0000,
    1000.0000,
    '2026-08-20 16:15:00+00'
);


-- ============================================================
-- 5. ORDERS
--
-- Account ACC-10001:
--   - FILLED BUY AAPL
--   - FILLED SELL AAPL
--   - FILLED BUY MSFT
--   - NEW BUY SPY
--   - CANCELLED BUY MSFT
--
-- Account ACC-10002:
--   - REJECTED BUY due to insufficient funds
--
-- Account ACC-10004:
--   - Historical FILLED BUY of delisted OLDCO
--
-- No orders are placed for the SUSPENDED account.
-- No orders are placed after the CLOSED account was closed.
-- ============================================================


-- Filled AAPL BUY
INSERT INTO orders (
    account_id,
    account_reference,
    instrument_id,
    side,
    quantity,
    price,
    status,
    idempotency_key,
    created_at
)
VALUES
(
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = 'ACC-10001'
    ),
    'ACC-10001',
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'AAPL'
    ),
    'BUY',
    10.00000000,
    150.00000000,
    'FILLED',
    'seed-idempotency-0001',
    '2026-02-10 10:15:00+00'
);


-- Filled AAPL SELL
INSERT INTO orders (
    account_id,
    account_reference,
    instrument_id,
    side,
    quantity,
    price,
    status,
    idempotency_key,
    created_at
)
VALUES
(
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = 'ACC-10001'
    ),
    'ACC-10001',
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'AAPL'
    ),
    'SELL',
    2.00000000,
    160.00000000,
    'FILLED',
    'seed-idempotency-0002',
    '2026-03-18 13:20:00+00'
);


-- Filled MSFT BUY
INSERT INTO orders (
    account_id,
    account_reference,
    instrument_id,
    side,
    quantity,
    price,
    status,
    idempotency_key,
    created_at
)
VALUES
(
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = 'ACC-10001'
    ),
    'ACC-10001',
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'MSFT'
    ),
    'BUY',
    5.00000000,
    400.00000000,
    'FILLED',
    'seed-idempotency-0003',
    '2026-04-22 14:10:00+00'
);


-- NEW / working order
INSERT INTO orders (
    account_id,
    account_reference,
    instrument_id,
    side,
    quantity,
    price,
    status,
    idempotency_key,
    created_at
)
VALUES
(
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = 'ACC-10001'
    ),
    'ACC-10001',
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'SPY'
    ),
    'BUY',
    5.00000000,
    500.00000000,
    'NEW',
    'seed-idempotency-0004',
    '2026-08-20 10:30:00+00'
);


-- CANCELLED order
INSERT INTO orders (
    account_id,
    account_reference,
    instrument_id,
    side,
    quantity,
    price,
    status,
    idempotency_key,
    created_at
)
VALUES
(
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = 'ACC-10001'
    ),
    'ACC-10001',
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'MSFT'
    ),
    'BUY',
    2.00000000,
    390.00000000,
    'CANCELLED',
    'seed-idempotency-0005',
    '2026-07-05 11:45:00+00'
);


-- REJECTED order:
-- Account only has $25 but attempts to buy $2,000 worth.
INSERT INTO orders (
    account_id,
    account_reference,
    instrument_id,
    side,
    quantity,
    price,
    status,
    idempotency_key,
    created_at
)
VALUES
(
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = 'ACC-10002'
    ),
    'ACC-10002',
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'AAPL'
    ),
    'BUY',
    10.00000000,
    200.00000000,
    'REJECTED',
    'seed-idempotency-0006',
    '2026-06-12 09:30:00+00'
);


-- Historical filled order against a now-delisted instrument.
INSERT INTO orders (
    account_id,
    account_reference,
    instrument_id,
    side,
    quantity,
    price,
    status,
    idempotency_key,
    created_at
)
VALUES
(
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = 'ACC-10004'
    ),
    'ACC-10004',
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'OLDCO'
    ),
    'BUY',
    10.00000000,
    100.00000000,
    'FILLED',
    'seed-idempotency-0007',
    '2026-02-25 10:00:00+00'
);


-- ============================================================
-- 6. TRANSACTIONS
--
-- Only FILLED orders produce completed transactions.
-- ============================================================

INSERT INTO transactions (
    order_id,
    instrument_id,
    quantity,
    price,
    executed_at,
    status
)
VALUES
(
    (
        SELECT order_id
        FROM orders
        WHERE idempotency_key = 'seed-idempotency-0001'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'AAPL'
    ),
    10.00000000,
    150.00000000,
    '2026-02-10 10:16:00+00',
    'COMPLETED'
),
(
    (
        SELECT order_id
        FROM orders
        WHERE idempotency_key = 'seed-idempotency-0002'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'AAPL'
    ),
    2.00000000,
    160.00000000,
    '2026-03-18 13:21:00+00',
    'COMPLETED'
),
(
    (
        SELECT order_id
        FROM orders
        WHERE idempotency_key = 'seed-idempotency-0003'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'MSFT'
    ),
    5.00000000,
    400.00000000,
    '2026-04-22 14:11:00+00',
    'COMPLETED'
),
(
    (
        SELECT order_id
        FROM orders
        WHERE idempotency_key = 'seed-idempotency-0007'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'OLDCO'
    ),
    10.00000000,
    100.00000000,
    '2026-02-25 10:01:00+00',
    'COMPLETED'
);


-- ============================================================
-- 7. PORTFOLIO HOLDINGS
--
-- These reconcile with FILLED orders.
--
-- ACC-10001:
--
-- AAPL:
--   +10 BUY
--    -2 SELL
--   = 8 shares
--   Average price remains $150 after the sale.
--
-- MSFT:
--   +5 BUY
--   = 5 shares @ $400
--
-- ACC-10004:
--
-- OLDCO:
--   +10 BUY
--   = 10 shares @ $100
--
-- OLDCO is deliberately non-tradable but remains referenced
-- by historical order and holding.
-- ============================================================

INSERT INTO portfolio_holdings (
    portfolio_id,
    instrument_id,
    quantity,
    average_price,
    last_updated
)
VALUES
(
    (
        SELECT p.portfolio_id
        FROM portfolios p
        JOIN accounts a
            ON a.account_id = p.account_id
        WHERE a.account_reference = 'ACC-10001'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'AAPL'
    ),
    8.00000000,
    150.00000000,
    '2026-08-20 16:00:00+00'
),
(
    (
        SELECT p.portfolio_id
        FROM portfolios p
        JOIN accounts a
            ON a.account_id = p.account_id
        WHERE a.account_reference = 'ACC-10001'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'MSFT'
    ),
    5.00000000,
    400.00000000,
    '2026-08-20 16:00:00+00'
),
(
    (
        SELECT p.portfolio_id
        FROM portfolios p
        JOIN accounts a
            ON a.account_id = p.account_id
        WHERE a.account_reference = 'ACC-10004'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'OLDCO'
    ),
    10.00000000,
    100.00000000,
    '2026-08-20 16:15:00+00'
);