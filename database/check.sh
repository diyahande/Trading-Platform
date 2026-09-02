#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# --------------------------------------------------
# Load repository .env
# --------------------------------------------------

if [[ -f "$SCRIPT_DIR/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "$SCRIPT_DIR/.env"
    set +a
fi

echo "======================================"
echo " Sprint 3 Trade Database Check"
echo "======================================"

# --------------------------------------------------
# 1. Check required files
# --------------------------------------------------

echo
echo "[1/7] Checking required files..."

required_files=(
    "$SCRIPT_DIR/manifest.env"
    "$SCRIPT_DIR/apply.sh"
    "$SCRIPT_DIR/probes/duplicate-idempotency-key.sql"
    "$SCRIPT_DIR/probes/orphan-foreign-key.sql"
)

for file in "${required_files[@]}"; do
    if [[ ! -f "$file" ]]; then
        echo "FAIL: Missing $file"
        exit 1
    fi
done

echo "PASS: Required files exist."


# --------------------------------------------------
# 2. Load manifest
# --------------------------------------------------

echo
echo "[2/7] Reading manifest.env..."

set -a
# shellcheck disable=SC1091
source "$SCRIPT_DIR/manifest.env"
set +a

required_vars=(
    APPLY_COMMAND
    ACCOUNTS_TABLE
    ACCOUNT_STATE_COLUMN
    ACCOUNT_STATE_ACTIVE
    ACCOUNT_STATE_SUSPENDED
    ACCOUNT_STATE_CLOSED
    ORDERS_TABLE
    ORDER_IDEMPOTENCY_KEY_COLUMN
)

for var in "${required_vars[@]}"; do
    if [[ -z "${!var:-}" ]]; then
        echo "FAIL: $var is not defined in manifest.env"
        exit 1
    fi
done

echo "PASS: manifest.env contains required variables."


# --------------------------------------------------
# 3. Check apply command
# --------------------------------------------------

echo
echo "[3/7] Checking apply command..."

if [[ ! -f "$SCRIPT_DIR/apply.sh" ]]; then
    echo "FAIL: apply.sh not found"
    exit 1
fi

echo "PASS: apply.sh exists."


# --------------------------------------------------
# 4. Check migrations
# --------------------------------------------------

echo
echo "[4/7] Checking migrations..."

migration_count=0

for migration in "$SCRIPT_DIR"/migrations/*.sql; do
    [[ -e "$migration" ]] || continue

    migration_count=$((migration_count + 1))

    filename="$(basename "$migration")"

    if [[ ! "$filename" =~ ^[0-9]{3}_.*\.sql$ ]]; then
        echo "FAIL: Migration does not use three-digit numbering: $filename"
        exit 1
    fi
done

if [[ "$migration_count" -eq 0 ]]; then
    echo "FAIL: No migration files found"
    exit 1
fi

echo "PASS: Found $migration_count migration file(s)."


# --------------------------------------------------
# 5. Check database connection and account states
# --------------------------------------------------

echo
echo "[5/7] Checking account states..."

TARGET_DATABASE="${TARGET_DATABASE:-${POSTGRES_DB:-}}"

if [[ -z "$TARGET_DATABASE" ]]; then
    echo "FAIL: TARGET_DATABASE/POSTGRES_DB not available"
    exit 1
fi

echo "Using database: $TARGET_DATABASE"

state_sql="
SELECT
    COUNT(*) FILTER (
        WHERE ${ACCOUNT_STATE_COLUMN} = '${ACCOUNT_STATE_ACTIVE}'
    ),
    COUNT(*) FILTER (
        WHERE ${ACCOUNT_STATE_COLUMN} = '${ACCOUNT_STATE_SUSPENDED}'
    ),
    COUNT(*) FILTER (
        WHERE ${ACCOUNT_STATE_COLUMN} = '${ACCOUNT_STATE_CLOSED}'
    )
FROM ${ACCOUNTS_TABLE};
"

state_counts="$(
    psql \
        -v ON_ERROR_STOP=1 \
        --dbname="$TARGET_DATABASE" \
        --tuples-only \
        --no-align \
        --command="$state_sql"
)"

IFS='|' read -r active_count suspended_count closed_count <<< "$state_counts"

if [[ "$active_count" -lt 1 ]]; then
    echo "FAIL: No ${ACCOUNT_STATE_ACTIVE} account found"
    exit 1
fi

if [[ "$suspended_count" -lt 1 ]]; then
    echo "FAIL: No ${ACCOUNT_STATE_SUSPENDED} account found"
    exit 1
fi

if [[ "$closed_count" -lt 1 ]]; then
    echo "FAIL: No ${ACCOUNT_STATE_CLOSED} account found"
    exit 1
fi

echo "PASS: All three account states exist."

# --------------------------------------------------
# 6. Duplicate idempotency-key probe
# --------------------------------------------------

echo
echo "[6/7] Testing duplicate idempotency key..."

duplicate_output="$(mktemp)"
duplicate_error="$(mktemp)"

trap 'rm -f "$duplicate_output" "$duplicate_error"' EXIT

if psql \
    -v ON_ERROR_STOP=1 \
    --dbname="$TARGET_DATABASE" \
    --file="$SCRIPT_DIR/probes/duplicate-idempotency-key.sql" \
    >"$duplicate_output" \
    2>&1
then
    if grep -q "SQLSTATE 23505" "$duplicate_output"; then
        echo "PASS: Duplicate idempotency key rejected with SQLSTATE 23505."
    else
        echo "FAIL: Duplicate idempotency probe did not detect 23505"
        cat "$duplicate_output"
        exit 1
    fi
else
    echo "FAIL: Duplicate probe execution failed"
    cat "$duplicate_output"
    exit 1
fi


# --------------------------------------------------
# 7. Orphan foreign-key probe
# --------------------------------------------------

echo
echo "[7/7] Testing orphan foreign key..."

orphan_output="$(mktemp)"
orphan_error="$(mktemp)"

if psql \
    -v ON_ERROR_STOP=1 \
    --dbname="$TARGET_DATABASE" \
    --file="$SCRIPT_DIR/probes/orphan-foreign-key.sql" \
    >"$orphan_output" \
    2>&1
then
    if grep -q "SQLSTATE 23503" "$orphan_output"; then
        echo "PASS: Orphan foreign key rejected with SQLSTATE 23503."
    else
        echo "FAIL: Orphan FK probe did not detect 23503"
        cat "$orphan_output"
        exit 1
    fi
else
    echo "FAIL: Orphan FK probe execution failed"
    cat "$orphan_output"
    exit 1
fi


# --------------------------------------------------
# Finished
# --------------------------------------------------

echo
echo "======================================"
echo " ALL CHECKS PASSED"
echo "======================================"