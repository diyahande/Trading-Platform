package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Order history entry for an account.
 * Maps to OpenAPI OrderHistoryEntry schema.
 * Includes all orders: accepted, filled, rejected, and cancelled.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderHistoryEntry {

    /**
     * Order identifier displayed with ORD- prefix
     */
    private String orderId;

    /**
     * Account reference UUID
     */
    private String accountId;

    /**
     * Instrument symbol
     */
    private String symbol;

    /**
     * Order side: BUY or SELL
     */
    private String side;

    /**
     * Order quantity in whole units
     */
    private Integer quantity;

    /**
     * Limit price submitted with the order
     */
    private BigDecimal price;

    /**
     * Price the Trade Executor filled at.
     * Null until the order is FILLED.
     */
    private BigDecimal executedPrice;

    /**
     * Order status: NEW, FILLED, REJECTED, or CANCELLED
     */
    private String status;

    /**
     * Client-generated idempotency key
     */
    private String idempotencyKey;

    /**
     * Order creation timestamp
     */
    
    private OffsetDateTime createdOn;
}
