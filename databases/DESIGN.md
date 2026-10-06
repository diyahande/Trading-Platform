# Trade Retention, Incremental Extraction and Growth

## 1. Schema Overview

The current trading schema separates customer information, trading accounts, instruments, orders, executed transactions, portfolios and portfolio holdings.

The main entities are:

| Table | Purpose |
|---|---|
| `users` | Stores customer identity and contact information. |
| `accounts` | Stores trading accounts belonging to users, including cash balance and account status. |
| `instruments` | Stores tradable securities and their market metadata. |
| `orders` | Stores the customer's trading request and its lifecycle state. |
| `transactions` | Stores the resulting executed trade information. |
| `portfolios` | Stores the portfolio-level value associated with an account. |
| `portfolio_holdings` | Stores the quantity and average price of each instrument held in a portfolio. |

The relationships represented by the schema are:

```text
users
  |
  +----< accounts
            |
            +----< orders >---- instruments
            |
            +---- portfolios
                       |
                       +----< portfolio_holdings >---- instruments

orders
  |
  +----< transactions >---- instruments
```

The design keeps the customer's request (`orders`) separate from the resulting execution (`transactions`). Portfolio and holding information is also kept separate from transaction history so that current portfolio state does not replace the historical record of executed trades.

The current schema contains the following key identifiers:

* `users.user_id` is the primary key for customers.
* `accounts.account_id` is the primary key for trading accounts and `accounts.user_id` links an account to a user.
* `instruments.instrument_id` is the primary key for tradable instruments.
* `orders.order_id` is the primary key for customer orders.
* `orders.account_id` and `orders.instrument_id` associate an order with its account and instrument.
* `transactions.transaction_id` is the primary key for executed trades.
* `transactions.order_id` links an execution to its originating order.
* `transactions.instrument_id` links the execution to the traded instrument.
* `portfolios.portfolio_id` is the primary key for a portfolio and `portfolios.account_id` associates it with an account.
* `portfolio_holdings.holding_id` is the primary key for a holding and `portfolio_holdings.portfolio_id` / `instrument_id` associate it with a portfolio and instrument.

---

## 2. Executed Trade Retention

An order represents the customer's trading intent: the account, instrument, side, requested quantity, requested price, idempotency key, receipt time and lifecycle state. An executed trade represents what actually happened in the market and therefore needs to retain information beyond the original order.

The design retains executed trades at **execution/trade grain**: one row in `transactions` represents one completed execution associated with an order. The order remains the source of the original request, while the transaction record is the immutable record of the resulting trade. This distinction is important because the order describes what was requested, whereas the transaction describes what was actually filled.

For each executed trade, the retained information includes:

* a unique `transaction_id`;
* the originating `order_id`;
* the `instrument_id`;
* executed quantity in `quantity`;
* executed price in `price`;
* execution timestamp in `executed_at`;
* execution status in `status`.

The account can be resolved from `transactions.transaction_id -> transactions.order_id -> orders.account_id`. The current `transactions` table does not contain a separate `account_id` column.

The design intentionally does not copy every order attribute into the transaction record. Information such as the customer's idempotency key, `side`, original requested quantity, original requested price and order creation timestamp already belongs to the order. Duplicating those fields would increase storage and create the risk that two copies of the same fact diverge.

The transaction record is populated by the **Trade/Order execution component**, not by the client. The client submits an order and the order is first recorded in the appropriate lifecycle state. Once the order is executed, the execution component records the resulting transaction and moves the order into its terminal `EXECUTED` state. Orders that are rejected or cancelled do not produce an executed-trade record.

The current model has no explicit half-filled state in `orders`. Therefore, the retained transaction record represents a completed execution rather than an intermediate fill state. The `transactions.order_id` relationship does not prevent multiple transactions from being associated with one order, so the model can support multiple execution rows for a single order if partial or multiple fills are introduced later.

Executed trade records are treated as historical facts. Once written, they should not be casually updated or deleted. Corrections should be represented using an explicit correction/reversal mechanism rather than silently changing the historical execution. This gives downstream reporting a stable audit trail.

The `transactions` table therefore becomes the durable source for historical trade reporting, while `orders` remain the source for client request and order-lifecycle information.

---

## 3. Population and Consistency

The order is recorded when it is received. The transaction record is created only when the execution component confirms that the order was executed.

Conceptually the flow is:

```text
Client
  |
  | submit order + idempotency key
  v
Order service
  |
  | create order
  v
orders
  |
  | execution
  v
Execution component
  |
  +----> transactions record
  |
  +----> order becomes EXECUTED
  |
  +----> portfolio / holding update
```

The transaction record must reference the originating order with a foreign key. This prevents an execution from existing without an identifiable source order.

The transaction's quantity and price are stored independently from the requested quantity and price because the executed values are facts about what happened, not merely a copy of what the customer requested.

Money and price values use exact `NUMERIC` types rather than floating-point types. This avoids rounding errors in financial calculations and keeps financial values consistent across `orders`, `transactions`, `portfolio_holdings`, `portfolios` and `accounts`.

The execution timestamp is retained independently as `executed_at`. It is the timestamp used for historical extraction rather than the order's `created_at` timestamp, because the extraction is based on completed trade activity.

The portfolio tables represent current state rather than a replacement for transaction history:

* `portfolios.current_value` represents the current portfolio value.
* `portfolios.invested_value` represents the invested value.
* `portfolio_holdings.quantity` represents the current quantity held.
* `portfolio_holdings.average_price` represents the current average acquisition price.
* `portfolio_holdings.last_updated` records when the holding was last updated.

These current-state values should not be used as the historical source for executed-trade extraction. Historical trade reporting should use `transactions`.

---

## 4. Incremental Sprint 7 Extraction

Sprint 7 must not scan the entire trade history every time an extract is generated. The extraction process should use the transaction execution timestamp as its incremental boundary.

The preferred query pattern is:

```sql
SELECT
    transaction_id,
    order_id,
    instrument_id,
    quantity,
    price,
    executed_at,
    status
FROM transactions
WHERE executed_at > :last_watermark
ORDER BY executed_at, transaction_id;
```

The extraction job stores a watermark representing the last successfully exported position. The pair `(executed_at, transaction_id)` should be treated as the logical cursor rather than `executed_at` alone.

This matters because multiple trades can have the same timestamp. Using only:

```sql
WHERE executed_at > :last_watermark
```

can miss rows if several records have identical timestamps and the watermark is advanced incorrectly. Ordering by both timestamp and the unique transaction identifier provides a deterministic cursor.

The extraction should therefore maintain:

```text
last_executed_at
last_transaction_id
```

and query using the equivalent of:

```sql
WHERE
    executed_at > :last_executed_at
    OR (
        executed_at = :last_executed_at
        AND transaction_id > :last_transaction_id
    )
ORDER BY executed_at, transaction_id;
```

The watermark is advanced only after the corresponding batch has been successfully written to its destination. If the export fails, the previous watermark is retained and the batch can be retried.

This gives an at-least-once extraction model. The downstream consumer should therefore be able to tolerate retries, preferably using `transaction_id` as its deduplication key.

An index should support the extraction pattern:

```sql
CREATE INDEX idx_transactions_extraction
ON transactions (executed_at, transaction_id);
```

With this index, the database can seek directly to the point represented by the watermark instead of scanning historical transactions.

For an extract that also needs customer/account context, the transaction can be joined back to the order and account:

```sql
SELECT
    t.transaction_id,
    t.order_id,
    o.account_id,
    o.account_reference,
    t.instrument_id,
    t.quantity,
    t.price,
    t.executed_at,
    t.status
FROM transactions t
JOIN orders o
    ON o.order_id = t.order_id
WHERE
    t.executed_at > :last_executed_at
    OR (
        t.executed_at = :last_executed_at
        AND t.transaction_id > :last_transaction_id
    )
ORDER BY t.executed_at, t.transaction_id;
```

The extraction boundary should remain based on `transactions.executed_at`, not on `orders.created_at`, because an order may be created before its execution.

---

## 5. Growth to 100x Current Volume

The design assumes that the number of executed trades will grow substantially. A 100x increase should not require a redesign of the application-facing order model.

The main scaling consideration is the `transactions` table because it is append-heavy and grows with every executed trade. The incremental extraction index grows with it, but the extraction workload remains proportional to the new records rather than the entire historical table.

At 100x volume, the expected costs are:

* more storage for immutable transaction records;
* a larger `(executed_at, transaction_id)` index;
* additional write I/O when each transaction updates both the table and index;
* longer-running historical queries if users request very large unbounded date ranges;
* greater backup and restore time;
* increased work for portfolio and holding updates as transaction volume grows.

The write cost of the incremental extraction index is accepted because it directly enables the Sprint 7 requirement. A full-history scan on every extraction would move the cost from writes to repeated reads and would become increasingly expensive as history grows.

Queries should always provide a bounded time range or incremental watermark. Application queries should not depend on repeatedly scanning the complete transaction history.

The `users`, `accounts`, `instruments`, `orders` and portfolio tables are expected to grow differently from the transaction table. In particular, `transactions` is expected to grow with every completed execution, while the number of users, accounts and instruments grows more slowly.

Foreign keys and uniqueness constraints should remain indexed appropriately. The `orders.idempotency_key` field should remain uniquely constrained/indexed if the application relies on it to prevent duplicate order submission.

The database should be monitored for:

* transaction table size;
* transaction index size;
* write latency;
* order-to-transaction execution latency;
* extraction duration;
* extraction batch size;
* portfolio/holding update latency;
* backup and restore duration.

---

## 6. Partitioning, Archival and Retention Decision

Partitioning is **not required at the current scale**. Introducing partitions now would increase operational complexity, migration complexity and the number of failure modes without providing a demonstrated benefit.

The initial design therefore keeps the `transactions` table as a normal table with an index on:

```text
(executed_at, transaction_id)
```

The design should be revisited when transaction volume or operational measurements show that a single table is becoming a bottleneck. If partitioning becomes necessary, time-based range partitioning by execution month or another appropriate period would be the preferred direction because the data is naturally time-oriented and Sprint 7 already extracts it by execution time.

Archival is also deferred initially. Executed trades are financial history and should not be physically removed merely to reduce table size. If a formal retention policy is introduced later, archival should move old immutable transaction records to cheaper storage while preserving the ability to retrieve historical information.

The current recommendation is therefore:

1. Retain executed transactions in the primary database.
2. Do not delete historical transactions as part of normal application operation.
3. Use incremental extraction rather than full-history scans.
4. Add the `(executed_at, transaction_id)` index.
5. Resolve account information through `transactions -> orders -> accounts` when required.
6. Keep portfolio and holding tables as current-state representations rather than historical trade sources.
7. Monitor volume, storage and extraction performance.
8. Introduce time-based partitioning only when measurements justify the operational cost.
9. Introduce archival only when a business or regulatory retention policy requires it.

This is deliberately a **measure-before-optimise** approach. Partitioning and archival can provide substantial benefits at very large scale, but they also add operational work around migrations, backups, restores, monitoring and querying. At the current stage, the simpler indexed design provides incremental extraction while keeping the operational model easy to understand and operate.
