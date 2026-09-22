package com.capstone.controller;

import com.capstone.dto.OrderHistoryEntry;
import com.capstone.dto.OrderResponse;
import com.capstone.dto.PlaceOrderRequest;
import com.capstone.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * OrderController handles all HTTP endpoints for order operations.
 * Implements the trade-api.yaml contract exactly.
 *
 * Security: JWT authentication validates all /api/* requests.
 * Account authorization verified before each operation.
 */
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /api/v1/orders
     * Place a new order with comprehensive validation.
     *
     * Business rules enforced (in order):
    * 1. Token's account key must match request account reference (ACC-403)
     * 2. Account must exist (ACC-404)
     * 3. Account must be active (ACC-403)
     * 4. Instrument must exist and be tradable (INS-404)
     * 5. Quantity must be greater than zero (VAL-422)
     * 6. Price must be greater than zero (VAL-422)
     * 7. BUY: sufficient cash balance (ORD-400)
     * 8. SELL: sufficient holdings (ORD-409)
     * 9. Idempotency key must be unique (ORD-409)
     *
     * @param request PlaceOrderRequest containing order details
     * @param authentication resolved authenticated principal from JWT
     * @return OrderResponse with placed order details and status
     */
    @PostMapping("/api/v1/orders")
    public ResponseEntity<OrderResponse> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
            orderService.placeOrderForAuthorizedAccount(request, authentication.getName())
        );
    }

    /**
     * DELETE /api/v1/orders/{id}
     * Cancel a working order (status must be NEW).
     *
     * An order that is FILLED, REJECTED, or CANCELLED cannot be cancelled.
     * The cancellation is guarded inside a database transaction to prevent
     * races with the Trade Executor.
     *
     * @param id
     * @param authentication
     * @return
     */
    @DeleteMapping("/api/v1/orders/{id}")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(
                orderService.cancelOrderForAuthorizedAccount(id, authentication.getName())
        );
    }

    /**
    * GET /api/v1/accounts/{accountRef}/orders
     * Retrieve order history for an account.
     *
     * Returns every order recorded against the account, newest first.
     * Includes rejected and cancelled orders (full audit trail).
     *
    * @param accountRef Account reference (UUID)
     * @param status Optional filter by order status
     * @param from Optional start date for filtering
     * @param to Optional end date for filtering
    * @param authentication resolved authenticated principal from JWT
     * @return List of OrderHistoryEntry sorted by creation date (newest first)
     */
    @GetMapping("/api/v1/accounts/{accountRef}/orders")
    public ResponseEntity<List<OrderHistoryEntry>> getOrders(
            @PathVariable String accountRef,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            Authentication authentication) {
        return ResponseEntity.ok(
                orderService.getOrderHistoryForAuthorizedAccount(accountRef, status, from, to, authentication.getName())
        );
    }

}
