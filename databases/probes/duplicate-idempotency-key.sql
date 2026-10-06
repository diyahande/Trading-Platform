BEGIN;

-- First insert: should succeed
INSERT INTO orders (
    account_id,
    account_reference,
    instrument_id,
    side,
    quantity,
    price,
    status,
    idempotency_key
)
SELECT
    a.account_id,
    a.account_reference,
    i.instrument_id,
    'BUY',
    1.00000000,
    100.00000000,
    'NEW',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
FROM accounts a
CROSS JOIN instruments i
WHERE a.status = 'ACTIVE'
  AND i.is_tradable = TRUE
LIMIT 1;


-- Second insert: same idempotency key.
-- This MUST fail with SQLSTATE 23505.
DO $$
BEGIN
    BEGIN

        INSERT INTO orders (
            account_id,
            account_reference,
            instrument_id,
            side,
            quantity,
            price,
            status,
            idempotency_key
        )
        SELECT
            a.account_id,
            a.account_reference,
            i.instrument_id,
            'BUY',
            1.00000000,
            100.00000000,
            'NEW',
            'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
        FROM accounts a
        CROSS JOIN instruments i
        WHERE a.status = 'ACTIVE'
          AND i.is_tradable = TRUE
        LIMIT 1;


        -- If we get here, the UNIQUE constraint failed to reject
        -- the duplicate key.
        RAISE EXCEPTION 'Duplicate idempotency key was accepted';

    EXCEPTION
        WHEN unique_violation THEN
            RAISE NOTICE 'SQLSTATE 23505';
    END;
END
$$;

ROLLBACK;