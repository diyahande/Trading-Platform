# Trade API Service

REST API service for managing trading accounts, orders, and portfolios. Accepts HTTP requests, persists data to PostgreSQL, and publishes orders to Kafka for execution by the Executor service.

## Overview

The Trade API provides:
- **User Management**: Authentication and JWT tokens
- **Account Management**: Create/manage trading accounts
- **Order Management**: Place, retrieve, cancel orders
- **Portfolio Management**: View holdings and performance
- **Instrument Data**: Security definitions and metadata
- **Kafka Integration**: Publishes orders for async execution

## Prerequisites

- Java 21+
- Maven 3.8+
- PostgreSQL 12+
- Kafka
- Authentication Stub (for JWT token generation)


## Running the Service


## API Endpoints

### Authentication
- `POST /auth/login` - Authenticate user
- `POST /auth/logout` - Logout user

### Accounts
- `GET /accounts` - List all accounts
- `GET /accounts/{accountId}` - Get account details
- `POST /accounts` - Create new account
- `PUT /accounts/{accountId}` - Update account

### Orders
- `GET /orders` - List all orders (paginated)
- `GET /orders/{orderId}` - Get order details
- `POST /orders` - Place new order
- `PUT /orders/{orderId}/cancel` - Cancel order

### Portfolios
- `GET /portfolios/{portfolioId}` - Get portfolio holdings
- `GET /portfolios/{portfolioId}/performance` - Get performance metrics

### Instruments
- `GET /instruments` - List all tradable instruments
- `GET /instruments/{symbol}` - Get instrument details

## Key Features

### Order Placement Flow

1. **Authentication**: User provides JWT token (from Auth Stub)
2. **Validation**: 
   - Check account exists and is ACTIVE
   - Validate instrument is tradable
   - Verify sufficient funds (for BUY orders)
3. **Persistence**: Order stored in PostgreSQL with status = NEW
4. **Kafka Publication**: Order event published to `orders` topic
5. **Async Execution**: Executor service consumes and processes the order

### Kafka Integration

- **Produces to**: `orders` topic with ORDER_PLACED events
- **Consumes from**: `trade-events` topic for execution results
- **Keying**: Orders keyed by `orderId` for ordering guarantees

### Data Persistence

- PostgreSQL for relational data (accounts, orders, portfolios)
- Optimistic locking on accounts for concurrent order handling

## Kafka Topics

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `orders` | → Produce | Publish new orders for execution |
| `trade-events` | ← Consume | Receive execution results |

## Logging

Default log levels:
- `INFO` - Root logger
- `DEBUG` - `com.capstone` package
- `DEBUG` - Spring Web framework
- `DEBUG` - Hibernate SQL
- `TRACE` - Hibernate SQL type descriptors

## Database Schema

Requires tables:
- `users` - User accounts
- `accounts` - Trading accounts
- `orders` - Order records
- `portfolio_holdings` - Position records
- `instruments` - Security definitions
- `portfolios` - Account portfolios
- `transactions` - Trade settlement records

## Event Models

### ORDER_PLACED Event (Published)
```json
{
  "eventId": "uuid",
  "eventType": "ORDER_PLACED",
  "timestamp": "2026-09-18T06:29:31Z",
  "source": "trade-api",
  "version": 1,
  "payload": {
    "orderId": "order-123",
    "accountId": 1,
    "symbol": "AAPL",
    "side": "BUY",
    "quantity": 100,
    "price": "150.00",
    "idempotencyKey": "key-123",
    "createdAt": "2026-09-18T06:29:31Z"
  }
}
```

### TRADE_EVENT (Consumed)
```json
{
  "eventId": "uuid",
  "eventType": "ORDER_FILLED",
  "timestamp": "2026-09-18T06:29:32Z",
  "source": "trade-executor",
  "version": 1,
  "payload": {
    "orderId": "order-123",
    "accountId": 1,
    "symbol": "AAPL",
    "side": "BUY",
    "quantity": 100,
    "limitPrice": "150.00",
    "executionPrice": "149.99",
    "status": "FILLED",
    "reason": null,
    "cashDelta": "-14999.00",
    "positionAfter": 100,
    "averageCostAfter": "149.99",
    "executedAt": "2026-09-18T06:29:32Z"
  }
}
```

## Authentication

- Requires JWT token from Auth Stub
- Token format: Bearer token in Authorization header
- Secret stored in `JWT_SECRET` environment variable

## Error Handling

- `400 Bad Request` - Invalid order parameters
- `401 Unauthorized` - Missing/invalid JWT token
- `404 Not Found` - Account/order/instrument not found
- `409 Conflict` - Order already exists (idempotency check)
- `500 Internal Server Error` - Database or system error

## Port

Default: `8083`

## Dependencies

- Spring Boot 3.2.x
- Spring Data JPA
- Spring Kafka 4.1.x
- PostgreSQL Driver
- JWT (JSON Web Token) support
- Jackson for JSON processing

## Related Services

- **Executor**: Consumes orders and publishes execution results
- **Auth Stub**: Issues JWT tokens for authentication
- **PostgreSQL**: Persistent data store
- **Kafka**: Async order processing

## Troubleshooting

**Order not executing:**
- Check Kafka connectivity
- Verify Executor service is running
- Check `orders` topic has messages

**Database errors:**
- Verify `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- Check PostgreSQL is running
- Verify database schema exists

**JWT token errors:**
- Verify `JWT_SECRET` matches Auth Stub value
- Check token expiration
- Verify Authorization header format

**Kafka connection errors:**
- Verify `KAFKA_BOOTSTRAP_SERVERS` is correct
- Check network connectivity to Kafka broker
