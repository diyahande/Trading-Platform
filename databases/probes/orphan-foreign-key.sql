BEGIN;

-- Attempt to insert an order referencing an account
-- that does not exist.
--
-- The combination (999999999, 'NONEXISTENT-ACCOUNT')
-- must not exist in accounts.
--
-- The database must reject this with SQLSTATE 23503.

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
            999999999,
            'NONEXISTENT-ACCOUNT',
            i.instrument_id,
            'BUY',
            1.00000000,
            100.00000000,
            'NEW',
            'cccccccc-cccc-cccc-cccc-cccccccccccc'
        FROM instruments i
        LIMIT 1;


        -- If the INSERT reaches this point, the FK
        -- did not reject the orphan account.
        RAISE EXCEPTION 'EXPECTED_FOREIGN_KEY_ERROR_NOT_RAISED';


    EXCEPTION
        WHEN foreign_key_violation THEN
            RAISE NOTICE 'SQLSTATE 23503';
    END;
END
$$;

ROLLBACK;