package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response for a placed or cancelled order.
 * Maps to OpenAPI OrderResponse schema.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    /**
     * Order identifier displayed with ORD- prefix over stored UUID.
     * Example: ORD-6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e
     */
    private String orderId;

    /**
     * Order status: NEW, FILLED, REJECTED, or CANCELLED
     */
    private String status;

    /**
     * Human-readable outcome message (display only).
     * Never branch on this string.
     */
    private String message;

    /**
     * Instrument symbol
     */
    private String symbol;

    /**
     * Order side: BUY or SELL
     */
    private String side;

    /**
     * Quantity in whole units
     */
    private Integer quantity;

    /**
     * Limit price per unit
     */
    private BigDecimal price;
}
