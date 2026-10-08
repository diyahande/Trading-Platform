#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load auth-service .env if present.
if [[ -f "$SCRIPT_DIR/../.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$SCRIPT_DIR/../.env"
  set +a
fi

if [[ -n "${DATABASE_URL:-}" ]]; then
  DB_CONN="$DATABASE_URL"
else
  required_vars=(DB_USERNAME DB_PASSWORD)
  for var in "${required_vars[@]}"; do
    if [[ -z "${!var:-}" ]]; then
      echo "Error: $var is not set. Set it in auth-service/.env or export it."
      exit 1
    fi
  done

  DB_HOST="${DB_HOST:-localhost}"
  DB_PORT="${DB_PORT:-5432}"
  DB_NAME="${DB_NAME:-capstone}"

  DB_CONN="postgresql://${DB_USERNAME}:${DB_PASSWORD}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
fi

DB_SCHEMA="${DB_SCHEMA:-auth_service}"

echo "Applying auth DB SQL files..."
for file in "$SCRIPT_DIR"/*.sql; do
  [[ -f "$file" ]] || continue
  echo "Running $(basename "$file")"
  PGOPTIONS="-c search_path=$DB_SCHEMA" psql -v ON_ERROR_STOP=1 "$DB_CONN" -f "$file"
done

echo "Auth DB setup completed successfully."
