#!/bin/bash

set -e

echo "Loading environment variables..."

if [ -f ".env" ]; then
    export $(grep -v '^#' .env | xargs)
fi

set -a
set +a

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
echo

run_sql_files() {
    local directory="$1"
    local description="$2"

    echo "$description"

    local found=false

    for file in "$directory"/*.sql; do
        if [ ! -f "$file" ]; then
            continue
        fi

        found=true

        echo "Running: $file"

        PGPASSWORD="$PGPASSWORD" psql \
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