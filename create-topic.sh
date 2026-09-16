#!/usr/bin/env bash

set -euo pipefail

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVERS:-kafka:29092}"

create_topic() {
    local topic="$1"
    local partitions="$2"
    local retention_ms="$3"

    echo "Creating topic: ${topic}"

    kafka-topics.sh \
        --bootstrap-server "${BOOTSTRAP_SERVER}" \
        --create \
        --if-not-exists \
        --topic "${topic}" \
        --partitions "${partitions}" \
        --replication-factor 1 \
        --config retention.ms="${retention_ms}"
}

# Main topics
create_topic "orders"       3 604800000
create_topic "trade-events" 3 2592000000
create_topic "market-data"  6 86400000

# Dead-letter topics
create_topic "orders.DLT"       3 2592000000
create_topic "trade-events.DLT" 3 2592000000
create_topic "market-data.DLT"  6 2592000000

echo "All Kafka topics created successfully."