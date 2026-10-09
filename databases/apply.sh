#!/bin/bash

set -e

# Run from this folder: the SQL paths below are relative to it.
cd "$(dirname "$0")"

echo "Loading environment variables..."

ROOT_ENV_FILE="../.env"
if [ -f "$ROOT_ENV_FILE" ]; then
    while IFS='=' read -r key value; do
        key="${key%$'\r'}"
        value="${value%$'\r'}"

        case "$key" in
            ''|'#'*) continue ;;
        esac

        if [ -z "${!key+x}" ]; then
            export "$key=$value"
        fi
    done < "$ROOT_ENV_FILE"
fi

set -a
set +a

DB_SCHEMA="${DB_SCHEMA:-stock_trading}"

# Check required variables
required_vars=(
    PGHOST
    PGPORT
    POSTGRES_DB
    PGUSER
    PGPASSWORD
)

for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
        echo "Error: $var is not set in .env"
        exit 1
    fi
done

echo "Connecting to database: $POSTGRES_DB"
echo "Host: $PGHOST"
echo "Port: $PGPORT"
echo "Schema: $DB_SCHEMA"
echo

run_sql_files() {
    local directory="$1"
    local description="$2"

    echo "$description"

    local found=false

    for file in "postgresql/$directory"/*.sql; do
        if [ ! -f "$file" ]; then
            continue
        fi

        found=true

        echo "Running: $file"

        PGPASSWORD="$PGPASSWORD" PGOPTIONS="-c search_path=$DB_SCHEMA" psql \
            -v ON_ERROR_STOP=1 \
            -h "$PGHOST" \
            -p "$PGPORT" \
            -U "$PGUSER" \
            -d "$POSTGRES_DB" \
            -f "$file"

        echo "Completed: $file"
        echo
    done

    if [ "$found" = false ]; then
        echo "Error: No SQL files found in $directory/"
        exit 1
    fi
}

# Run database migrations first
run_sql_files "migrations" "Running database migrations"

# Load seed data after migrations
run_sql_files "seed" "Loading seed data"

echo "Database setup completed successfully"