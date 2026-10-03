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
    '11111111-1111-4111-8111-111111111111',
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
    '22222222-2222-4222-8222-222222222222',
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
    '33333333-3333-4333-8333-333333333333',
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
    '44444444-4444-4444-8444-444444444444',
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
    'INFY.NS',
    'Infosys Limited (NSE)',
    'EQUITY',
    'INR',
    TRUE,
    '2025-12-11 09:00:00+00'
),
(
    'INFY.BO',
    'Infosys Limited (BSE)',
    'EQUITY',
    'INR',
    TRUE,
    '2025-12-11 09:05:00+00'
),
(
    'RELIANCE.NS',
    'Reliance Industries Limited (NSE)',
    'EQUITY',
    'INR',
    TRUE,
    '2025-12-11 09:10:00+00'
),
(
    'RELIANCE.BO',
    'Reliance Industries Limited (BSE)',
    'EQUITY',
    'INR',
    TRUE,
    '2025-12-11 09:15:00+00'
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
),
(
    'GOOGL',
    'Alphabet Inc.',
    'EQUITY',
    'USD',
    TRUE,
    '2025-12-05 09:00:00+00'
),
(
    'AMZN',
    'Amazon.com Inc.',
    'EQUITY',
    'USD',
    TRUE,
    '2025-12-08 09:00:00+00'
),
(
    'NFLX',
    'Netflix Inc.',
    'EQUITY',
    'USD',
    TRUE,
    '2025-12-10 09:00:00+00'
),
(
    'TSLA',
    'Tesla Inc.',
    'EQUITY',
    'USD',
    TRUE,
    '2025-12-12 09:00:00+00'
),
(
    'META',
    'Meta Platforms Inc.',
    'EQUITY',
    'USD',
    TRUE,
    '2025-12-15 09:00:00+00'
),
(
    'NVDA',
    'NVIDIA Corporation',
    'EQUITY',
    'USD',
    TRUE,
    '2025-11-20 09:00:00+00'
),
(
    'JNJ',
    'Johnson & Johnson',
    'EQUITY',
    'USD',
    TRUE,
    '2025-11-15 09:00:00+00'
),
(
    'PFE',
    'Pfizer Inc.',
    'EQUITY',
    'USD',
    TRUE,
    '2025-11-18 09:00:00+00'
),
(
    'UNH',
    'UnitedHealth Group',
    'EQUITY',
    'USD',
    TRUE,
    '2025-11-22 09:00:00+00'
),
(
    'JPM',
    'JPMorgan Chase & Co.',
    'EQUITY',
    'USD',
    TRUE,
    '2025-12-01 09:00:00+00'
),
(
    'BAC',
    'Bank of America',
    'EQUITY',
    'USD',
    TRUE,
    '2025-12-03 09:00:00+00'
),
(
    'GS',
    'Goldman Sachs Group Inc.',
    'EQUITY',
    'USD',
    TRUE,
    '2025-12-06 09:00:00+00'
),
(
    'WMT',
    'Walmart Inc.',
    'EQUITY',
    'USD',
    TRUE,
    '2025-11-25 09:00:00+00'
),
(
    'TM',
    'Toyota Motor Corporation',
    'EQUITY',
    'USD',
    TRUE,
    '2025-11-28 09:00:00+00'
),
(
    'KO',
    'The Coca-Cola Company',
    'EQUITY',
    'USD',
    TRUE,
    '2025-12-09 09:00:00+00'
),
(
    'XOM',
    'Exxon Mobil Corporation',
    'EQUITY',
    'USD',
    TRUE,
    '2025-11-10 09:00:00+00'
),
(
    'CVX',
    'Chevron Corporation',
    'EQUITY',
    'USD',
    TRUE,
    '2025-11-12 09:00:00+00'
),
(
    'QQQ',
    'Invesco QQQ Trust',
    'ETF',
    'USD',
    TRUE,
    '2025-12-18 09:00:00+00'
),
(
    'IVV',
    'iShares Core S&P 500 ETF',
    'ETF',
    'USD',
    TRUE,
    '2025-12-20 09:00:00+00'
),
(
    'BRK.B',
    'Berkshire Hathaway Inc.',
    'EQUITY',
    'USD',
    TRUE,
    '2025-12-22 09:00:00+00'
),
(
    'V',
    'Visa Inc.',
    'EQUITY',
    'USD',
    TRUE,
    '2025-12-25 09:00:00+00'
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
        WHERE account_reference = '11111111-1111-4111-8111-111111111111'
    ),
    18500.0000,
    15000.0000,
    '2026-08-20 16:00:00+00'
),
(
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = '22222222-2222-4222-8222-222222222222'
    ),
    0.0000,
    0.0000,
    '2026-08-20 16:05:00+00'
),
(
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = '33333333-3333-4333-8333-333333333333'
    ),
    0.0000,
    0.0000,
    '2026-08-20 16:10:00+00'
),
(
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = '44444444-4444-4444-8444-444444444444'
    ),
    1000.0000,
    1000.0000,
    '2026-08-20 16:15:00+00'
);


-- ============================================================
-- 5. ORDERS
--
-- Account 11111111-1111-4111-8111-111111111111:
--   - FILLED BUY AAPL
--   - FILLED SELL AAPL
--   - FILLED BUY MSFT
--   - NEW BUY SPY
--   - CANCELLED BUY MSFT
--
-- Account 22222222-2222-4222-8222-222222222222:
--   - REJECTED BUY due to insufficient funds
--
-- Account 44444444-4444-4444-8444-444444444444:
--   - Historical FILLED BUY of delisted OLDCO
--
-- No orders are placed for the SUSPENDED account.
-- No orders are placed after the CLOSED account was closed.
-- ============================================================


-- Filled AAPL BUY
INSERT INTO orders (
    order_id,
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
    '11111111-1111-1111-1111-111111111001',
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = '11111111-1111-4111-8111-111111111111'
    ),
    '11111111-1111-4111-8111-111111111111',
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
    order_id,
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
    '11111111-1111-1111-1111-111111111002',
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = '11111111-1111-4111-8111-111111111111'
    ),
    '11111111-1111-4111-8111-111111111111',
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
    order_id,
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
    '11111111-1111-1111-1111-111111111003',
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = '11111111-1111-4111-8111-111111111111'
    ),
    '11111111-1111-4111-8111-111111111111',
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
    order_id,
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
    '11111111-1111-1111-1111-111111111004',
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = '11111111-1111-4111-8111-111111111111'
    ),
    '11111111-1111-4111-8111-111111111111',
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
    order_id,
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
    '11111111-1111-1111-1111-111111111005',
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = '11111111-1111-4111-8111-111111111111'
    ),
    '11111111-1111-4111-8111-111111111111',
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
    order_id,
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
    '11111111-1111-1111-1111-111111111006',
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = '22222222-2222-4222-8222-222222222222'
    ),
    '22222222-2222-4222-8222-222222222222',
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
    order_id,
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
    '11111111-1111-1111-1111-111111111007',
    (
        SELECT account_id
        FROM accounts
        WHERE account_reference = '44444444-4444-4444-8444-444444444444'
    ),
    '44444444-4444-4444-8444-444444444444',
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
-- 11111111-1111-4111-8111-111111111111:
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
-- 44444444-4444-4444-8444-444444444444:
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
        WHERE a.account_reference = '11111111-1111-4111-8111-111111111111'
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
        WHERE a.account_reference = '11111111-1111-4111-8111-111111111111'
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
        WHERE a.account_reference = '44444444-4444-4444-8444-444444444444'
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


-- ============================================================
-- 8. WATCHLIST
-- ============================================================

INSERT INTO watchlist (
    user_id,
    instrument_id,
    name,
    created_at
)
VALUES
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'alice.johnson@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'AAPL'
    ),
    'My Tech Stocks',
    '2026-08-15 10:00:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'alice.johnson@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'MSFT'
    ),
    'My Tech Stocks',
    '2026-08-15 10:05:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'alice.johnson@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'SPY'
    ),
    'ETF Portfolio',
    '2026-08-18 14:20:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'bob.smith@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'MSFT'
    ),
    'Tech Watch',
    '2026-08-16 09:30:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'charlie.brown@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'AAPL'
    ),
    'Watchlist',
    '2026-08-17 11:15:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'alice.johnson@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'GOOGL'
    ),
    'Tech Giants',
    '2026-08-19 10:00:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'alice.johnson@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'AMZN'
    ),
    'Tech Giants',
    '2026-08-19 10:05:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'alice.johnson@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'NVDA'
    ),
    'AI Stocks',
    '2026-08-19 10:10:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'alice.johnson@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'TSLA'
    ),
    'Growth Stocks',
    '2026-08-19 10:15:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'alice.johnson@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'META'
    ),
    'Tech Giants',
    '2026-08-19 10:20:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'alice.johnson@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'QQQ'
    ),
    'ETF Portfolio',
    '2026-08-19 10:25:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'alice.johnson@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'JNJ'
    ),
    'Healthcare Stocks',
    '2026-08-19 10:30:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'bob.smith@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'AMZN'
    ),
    'Tech Watch',
    '2026-08-19 10:35:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'bob.smith@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'GOOGL'
    ),
    'Tech Watch',
    '2026-08-19 10:40:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'bob.smith@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'TSLA'
    ),
    'Growth Stocks',
    '2026-08-19 10:45:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'bob.smith@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'NFLX'
    ),
    'Entertainment',
    '2026-08-19 10:50:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'bob.smith@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'JPM'
    ),
    'Financial Sector',
    '2026-08-19 10:55:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'bob.smith@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'V'
    ),
    'Payment Systems',
    '2026-08-19 11:00:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'charlie.brown@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'GOOGL'
    ),
    'Watchlist',
    '2026-08-19 11:05:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'charlie.brown@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'XOM'
    ),
    'Energy Sector',
    '2026-08-19 11:10:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'charlie.brown@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'CVX'
    ),
    'Energy Sector',
    '2026-08-19 11:15:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'charlie.brown@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'KO'
    ),
    'Consumer Goods',
    '2026-08-19 11:20:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'charlie.brown@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'WMT'
    ),
    'Retail Sector',
    '2026-08-19 11:25:00+00'
),
(
    (
        SELECT user_id
        FROM users
        WHERE email = 'charlie.brown@example.com'
    ),
    (
        SELECT instrument_id
        FROM instruments
        WHERE symbol = 'IVV'
    ),
    'Index Funds',
    '2026-08-19 11:30:00+00'
);