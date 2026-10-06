# Executor Service

Order execution service that consumes `ORDER_PLACED` events from Kafka, executes trades against live market data (Fauxnance), and publishes `ORDER_FILLED` or `ORDER_REJECTED` events to the `trade-events` topic.

## Overview

The Executor service is responsible for:
- **Consuming** orders from the `orders` Kafka topic
- **Validating** orders (account status, instrument tradability, available funds)
- **Fetching** live market quotes from Fauxnance API
- **Applying** fill rules (checking if limit price meets bid/ask)
- **Settling** executed orders (updating accounts, portfolios, transactions in PostgreSQL)
- **Publishing** trade events to the `trade-events` Kafka topic
- **Polling** market data and publishing quotes to `market-quotes` topic

## Prerequisites

- Java 21+
- Maven 3.8+
- PostgreSQL 12+
- Kafka (bootstrap server: `10.8.78.68:8085`)
- Network access to Fauxnance API

## Configuration

All configuration is managed through environment variables. Set these before running:

### Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `SERVER_PORT` | Spring Boot server port | `8084` |
| `DB_URL` | PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/stock_trading_db` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `n3u3d4!` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers | `10.8.78.68:8085` |
| `FAUXNANCE_BASE_URL` | Fauxnance API base URL | `https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1` |
| `FAUXNANCE_API_KEY` | Fauxnance API key | `fnx_dev_w94amk7F63WTtF8RTkdEESXffMagvsC0` |
| `FAUXNANCE_MAX_RETRIES` | Quote fetch retry attempts | `3` |
| `FAUXNANCE_RETRY_DELAY_MS` | Delay between retries (ms) | `100` |
| `POLL_INTERVAL_SECONDS` | Market data poll interval | `60` |

## Running the Service

### Using `.env` file (Bash)

```bash
cd executor
set -a
source .env
set +a
mvn spring-boot:run
```

### Using PowerShell

```powershell
cd executor
$env:SERVER_PORT='8084'; `
$env:DB_URL='jdbc:postgresql://localhost:5432/stock_trading_db'; `
$env:DB_USERNAME='postgres'; `
$env:DB_PASSWORD='n3u3d4!'; `
$env:KAFKA_BOOTSTRAP_SERVERS='10.8.78.68:8085'; `
$env:FAUXNANCE_BASE_URL='https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1'; `
$env:FAUXNANCE_API_KEY='fnx_dev_w94amk7F63WTtF8RTkdEESXffMagvsC0'; `
$env:FAUXNANCE_MAX_RETRIES='3'; `
$env:FAUXNANCE_RETRY_DELAY_MS='100'; `
$env:POLL_INTERVAL_SECONDS='60'; `
mvn spring-boot:run
```

## Key Features

### Order Execution Flow

1. **Order Consumption**: `OrderPlacedConsumer` listens to `orders` topic
2. **Validation**: 
   - Order exists and status is NEW
   - Account exists and is ACTIVE
   - Instrument exists and is tradable
3. **Quote Fetching**: Requests live bid/ask from Fauxnance
4. **Fill Decision**: Applies FillRule
   - BUY: Fills if limit price ≥ ask price
   - SELL: Fills if limit price ≤ bid price
5. **Settlement** (transactional):
   - Updates order status to FILLED
   - Updates account cash balance (with optimistic locking)
   - Updates portfolio holdings
   - Inserts transaction record
6. **Publication**: Publishes ORDER_FILLED or ORDER_REJECTED to `trade-events`

### Market Data Polling

- Polls configured instruments every `POLL_INTERVAL_SECONDS`
- Batches requests to stay within Fauxnance quota (2000 requests/day)
- Publishes quotes to `market-quotes` topic for real-time pricing

### Error Handling

- **Transient Errors** (e.g., connection timeouts): Automatically retried
- **Permanent Errors** (e.g., malformed messages): Sent to Dead Letter Topic

## Kafka Topics

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `orders` | ← Consume | Incoming orders to execute |
| `trade-events` | → Publish | Execution results (FILLED/REJECTED) |
| `market-quotes` | → Publish | Market data for monitoring |

## Logging

Default log level: `DEBUG` for `com.capstone.executor`

Check logs for:
- `OrderPlacedConsumer received message` - Order consumption
- `FillRule decision` - Fill/reject decision
- `fillOrder() START` - Settlement process started
- `Trade event published successfully` - Event published to Kafka

## Database Schema

Requires tables:
- `accounts` - Account balance and versioning
- `orders` - Order tracking
- `portfolio_holdings` - Position tracking
- `instruments` - Security definitions
- `transactions` - Trade settlement records
- `portfolios` - Account portfolios

## Troubleshooting

**No ORDER_FILLED events published:**
- Check `FillRule decision` logs - verify bid/ask meets limit price
- Check `fillOrder() START` logs - verify settlement didn't throw exception
- Verify database connectivity and schema

**Kafka connection errors:**
- Verify `KAFKA_BOOTSTRAP_SERVERS` is correct
- Check network connectivity to Kafka broker

**Fauxnance API errors:**
- Verify `FAUXNANCE_API_KEY` is valid
- Check `FAUXNANCE_BASE_URL` is accessible

## Port

Default: `8084`

## Dependencies

- Spring Boot 3.2.x
- Spring Kafka 4.1.x
- MyBatis 3.5.x
- PostgreSQL Driver
- Fauxnance REST Client
