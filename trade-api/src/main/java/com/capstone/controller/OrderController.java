package com.capstone.controller;

import com.capstone.dto.OrderHistoryEntry;
import com.capstone.dto.OrderResponse;
import com.capstone.dto.PlaceOrderRequest;
import com.capstone.entity.Order;
import com.capstone.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.annotation.security.PermitAll;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * OrderController handles all HTTP endpoints for order operations.
 * Implements the trade-api.yaml contract exactly.
 *
 * Security: Temporarily disabled for Sprint 6 testing.
 * TODO: Enable JWT authentication once auth service is integrated.
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
     * 1. Account must exist (ACC-404)
     * 2. Account must be active (ACC-403)
     * 3. Instrument must exist and be tradable (INS-404)
     * 4. Quantity must be greater than zero (VAL-422)
     * 5. Price must be greater than zero (VAL-422)
     * 6. BUY: sufficient cash balance (ORD-400)
     * 7. SELL: sufficient holdings (ORD-409)
     * 8. Idempotency key must be unique (ORD-409)
     *
     * @param request PlaceOrderRequest containing order details
     * @return OrderResponse with placed order details and status
     * @throws IllegalArgumentException for validation failures with error codes
     */
    @PermitAll
    @PostMapping("/api/v1/orders")
    public ResponseEntity<OrderResponse> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request) {

        try {
            // Generate idempotency key - in production, this comes from the request
            String idempotencyKey = request.getIdempotencyKey();

            // Check for duplicate order using idempotency key
            Optional<Order> existingOrder = orderService.getOrderByIdempotencyKey(idempotencyKey);
            if (existingOrder.isPresent()) {
                // ORD-409: Idempotency key already used
                throw new IllegalArgumentException("ORD-409: Idempotency key has been used");
            }

            // Place the order (validates all business rules 1-7)
            Order order = orderService.placeOrder(request, idempotencyKey);

            // Map Order entity to OrderResponse DTO
            OrderResponse response = mapOrderToResponse(order, request.getSymbol());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            // Extract error code from message and return appropriate HTTP status
            String errorCode = extractErrorCode(e.getMessage());
            return handleValidationError(errorCode, e.getMessage());
        }
    }

    /**
     * DELETE /api/v1/orders/{id}
     * Cancel a working order (status must be NEW).
     *
     * An order that is FILLED, REJECTED, or CANCELLED cannot be cancelled.
     * The cancellation is guarded inside a database transaction to prevent
     * races with the Trade Executor.
     *
     * @param id Order identifier (Long)
     * @return OrderResponse with cancelled order details
     */
    @PermitAll
    @DeleteMapping("/api/v1/orders/{id}")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long id) {

        try {
            // Retrieve the order
            Optional<Order> orderOpt = orderService.getOrderById(id);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("ORD-404", "No order exists with that identifier"));
            }

            Order order = orderOpt.get();

            // Check if order can be cancelled (must be NEW)
            if (!order.getStatus().toString().equals("NEW")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(createErrorResponse("ORD-409", 
                                "The order is not in a cancellable state"));
            }

            // Cancel the order (updates status to CANCELLED)
            orderService.cancelOrder(id);

            // Retrieve updated order and return
            Order cancelledOrder = orderService.getOrderById(id)
                    .orElseThrow(() -> new IllegalStateException("Order disappeared after cancellation"));

            OrderResponse response = mapOrderToResponse(cancelledOrder, null);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            String errorCode = extractErrorCode(e.getMessage());
            return handleValidationError(errorCode, e.getMessage());
        }
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
     * @return List of OrderHistoryEntry sorted by creation date (newest first)
     */
    @PermitAll
    @GetMapping("/api/v1/accounts/{id}/orders")
    public ResponseEntity<List<OrderHistoryEntry>> getOrders(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        try {
            // Retrieve all orders for the account
            List<Order> orders = orderService.getOrdersByAccountId(id);

            // Map to OrderHistoryEntry DTOs
            List<OrderHistoryEntry> historyEntries = orders.stream()
                    .map(this::mapOrderToHistoryEntry)
                    .collect(Collectors.toList());

            // Apply optional filters
            if (status != null && !status.isEmpty()) {
                historyEntries = historyEntries.stream()
                        .filter(entry -> entry.getStatus().equals(status))
                        .collect(Collectors.toList());
            }

            if (from != null) {
                historyEntries = historyEntries.stream()
                        .filter(entry -> entry.getCreatedOn().isAfter(from))
                        .collect(Collectors.toList());
            }

            if (to != null) {
                historyEntries = historyEntries.stream()
                        .filter(entry -> entry.getCreatedOn().isBefore(to))
                        .collect(Collectors.toList());
            }

            return ResponseEntity.ok(historyEntries);

        } catch (IllegalArgumentException e) {
            String errorCode = extractErrorCode(e.getMessage());
            return ResponseEntity.status(getHttpStatus(errorCode))
                    .build();
        }
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

    /**
     * Extract error code from exception message.
     * Message format: "ERROR-CODE: description"
     */
    private String extractErrorCode(String message) {
        if (message != null && message.contains(":")) {
            return message.split(":")[0].trim();
        }
        return "UNKNOWN";
    }

    /**
     * Create an error response DTO.
     * For consistency with ErrorResponse schema.
     */
    private OrderResponse createErrorResponse(String errorCode, String message) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(errorCode);
        response.setStatus("ERROR");
        response.setMessage(message);
        return response;
    }

    /**
     * Map error code to HTTP status.
     * Following trade-api.yaml contract.
     */
    private HttpStatus getHttpStatus(String errorCode) {
        return switch (errorCode) {
            case "ACC-404" -> HttpStatus.NOT_FOUND;
            case "ACC-403" -> HttpStatus.FORBIDDEN;
            case "INS-404" -> HttpStatus.NOT_FOUND;
            case "VAL-422" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "ORD-400" -> HttpStatus.BAD_REQUEST;
            case "ORD-409" -> HttpStatus.CONFLICT;
            case "AUTH-401" -> HttpStatus.UNAUTHORIZED;
            case "ORD-404" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * Handle validation errors and return appropriate response.
     */
    private ResponseEntity<OrderResponse> handleValidationError(
            String errorCode,
            String message) {

        HttpStatus status = getHttpStatus(errorCode);
        OrderResponse errorResponse = new OrderResponse();
        errorResponse.setOrderId(errorCode);
        errorResponse.setStatus("ERROR");
        errorResponse.setMessage(message);

        return ResponseEntity.status(status).body(errorResponse);
    }
}
