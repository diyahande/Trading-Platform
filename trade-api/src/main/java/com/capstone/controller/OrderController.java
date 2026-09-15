package com.capstone.controller;

import com.capstone.dto.OrderHistoryEntry;
import com.capstone.dto.OrderResponse;
import com.capstone.dto.PlaceOrderRequest;
import com.capstone.entity.Order;
import com.capstone.exception.OrderNotFoundException;
import com.capstone.service.OrderService;
import com.capstone.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
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
    private final AccountService accountService;

    /**
     * POST /api/v1/orders
     * Place a new order with comprehensive validation.
     *
     * Business rules enforced (in order):
     * 1. Token's account key must match request account ID (ACC-403)
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
        String accountKey = authentication.getName();
        accountService.verifyAuthorizedAccount(request.getAccountId(), accountKey);

        String idempotencyKey = request.getIdempotencyKey();
        Order order = orderService.placeOrder(request, idempotencyKey);

        OrderResponse response = mapOrderToResponse(order, request.getSymbol());
        return ResponseEntity.ok(response);
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
        Optional<Order> orderOpt = orderService.getOrderById(id);
        if (orderOpt.isEmpty()) {
            throw new OrderNotFoundException(id);
        }

        Order order = orderOpt.get();

        String accountKey = authentication.getName();
        accountService.verifyAuthorizedAccount(order.getAccountId(), accountKey);

        orderService.cancelOrder(id);

        Order cancelledOrder = orderService.getOrderById(id)
                .orElseThrow(() -> new IllegalStateException("Order disappeared after cancellation"));

        OrderResponse response = mapOrderToResponse(cancelledOrder, null);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/accounts/{id}/orders
     * Retrieve order history for an account.
     *
     * Returns every order recorded against the account, newest first.
     * Includes rejected and cancelled orders (full audit trail).
     *
     * @param id Account identifier (numeric account key)
     * @param status Optional filter by order status
     * @param from Optional start date for filtering
     * @param to Optional end date for filtering
    * @param authentication resolved authenticated principal from JWT
     * @return List of OrderHistoryEntry sorted by creation date (newest first)
     */
    @GetMapping("/api/v1/accounts/{id}/orders")
    public ResponseEntity<List<OrderHistoryEntry>> getOrders(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Authentication authentication) {

        String accountKey = authentication.getName();
        accountService.verifyAuthorizedAccount(id, accountKey);

        List<Order> orders = orderService.getOrdersByAccountId(id);

        List<OrderHistoryEntry> historyEntries = orders.stream()
                .map(this::mapOrderToHistoryEntry)
                .toList();

        if (status != null && !status.isEmpty()) {
            historyEntries = historyEntries.stream()
                    .filter(entry -> entry.getStatus().equals(status))
                    .toList();
        }

        if (from != null) {
            historyEntries = historyEntries.stream()
                    .filter(entry -> entry.getCreatedOn().isAfter(from))
                    .toList();
        }

        if (to != null) {
            historyEntries = historyEntries.stream()
                    .filter(entry -> entry.getCreatedOn().isBefore(to))
                    .toList();
        }

        return ResponseEntity.ok(historyEntries);
    }

    /**
     * Map Order entity to OrderResponse DTO.
     * Formats orderId with ORD- prefix.
     */
    private OrderResponse mapOrderToResponse(Order order, String symbol) {
        OrderResponse response = new OrderResponse();
        response.setOrderId("ORD-" + order.getOrderId());
        response.setStatus(order.getStatus().toString());
        response.setMessage("Order " + order.getStatus().toString());
        response.setSymbol(symbol);
        response.setSide(order.getSide().toString());
        response.setQuantity(order.getQuantity().intValue());
        response.setPrice(order.getPrice());
        return response;
    }

    /**
     * Map Order entity to OrderHistoryEntry DTO.
     * For use in order history queries.
     */
    private OrderHistoryEntry mapOrderToHistoryEntry(Order order) {
        OrderHistoryEntry entry = new OrderHistoryEntry();
        entry.setOrderId("ORD-" + order.getOrderId());
        entry.setAccountId(order.getAccountId());
        entry.setSymbol(null); // TODO: Fetch instrument symbol from database
        entry.setSide(order.getSide().toString());
        entry.setQuantity(order.getQuantity().intValue());
        entry.setPrice(order.getPrice());
        entry.setExecutedPrice(null); // TODO: Set when order is FILLED
        entry.setStatus(order.getStatus().toString());
        entry.setIdempotencyKey(order.getIdempotencyKey());

        // Convert Instant to LocalDateTime
        LocalDateTime createdOn = LocalDateTime.ofInstant(
                order.getCreatedAt(),
                ZoneId.systemDefault()
        );
        entry.setCreatedOn(createdOn);

        return entry;
    }

}
