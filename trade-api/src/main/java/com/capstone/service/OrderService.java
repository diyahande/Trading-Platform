package com.capstone.service;

import com.capstone.dto.OrderHistoryEntry;
import com.capstone.dto.OrderResponse;
import com.capstone.dto.PlaceOrderRequest;
import com.capstone.entity.Account;
import com.capstone.entity.Order;
import com.capstone.enums.OrderSide;
import com.capstone.enums.OrderStatus;
import com.capstone.exception.DuplicateOrderException;
import com.capstone.exception.InsufficientFundsException;
import com.capstone.exception.InsufficientHoldingsException;
import com.capstone.exception.InvalidOrderException;
import com.capstone.exception.OrderNotFoundException;
import com.capstone.exception.OrderNotCancellableException;
import com.capstone.kafka.OrderPlacedEventPublisher;
import com.capstone.kafka.TransactionCommitHook;
import com.capstone.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * OrderService handles all order-related business logic for the trading platform.
 * Implements validation rules and coordinates between controllers and data layer.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final AccountService accountService;
    private final InstrumentService instrumentService;
    private final OrderPlacedEventPublisher orderPlacedEventPublisher;
    private final TransactionCommitHook transactionCommitHook;

    /**
     * Place order after account authorization and return API response payload.
     */
    @Transactional
    public OrderResponse placeOrderForAuthorizedAccount(PlaceOrderRequest request, String accountKey) {
        accountService.verifyAuthorizedAccount(request.getAccountId(), accountKey);
        Order order = placeOrder(request, request.getIdempotencyKey());
        return mapOrderToResponse(order, request.getSymbol());
    }

    /**
     * Cancel order after authorization and return updated API response payload.
     */
    public OrderResponse cancelOrderForAuthorizedAccount(UUID orderId, String accountKey) {
        Order order = getOrderById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));

        accountService.verifyAuthorizedAccount(order.getAccountReference(), accountKey);
        cancelOrder(orderId);

        Order cancelledOrder = getOrderById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order disappeared after cancellation"));

        return mapOrderToResponse(cancelledOrder, null);
    }

    /**
     * Retrieve and filter account order history after authorization.
     */
    public List<OrderHistoryEntry> getOrderHistoryForAuthorizedAccount(
            String accountReference,
            String status,
            OffsetDateTime from,
            OffsetDateTime to,
            String accountKey) {
        Account account = accountService.verifyAuthorizedAccount(accountReference, accountKey);

        List<OrderHistoryEntry> historyEntries = orderMapper.findOrderHistoryByAccountId(account.getAccountId());

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

        return historyEntries;
    }

    /**
     * Place a new order with comprehensive validation.
     * Validation rules (in order):
     * 1. Account must exist (ACC-404)
     * 2. Account must be active (ACC-403)
     * 3. Instrument must exist and be tradable (INS-404)
     * 4. Quantity must be positive (VAL-422)
     * 5. Price must be positive (VAL-422)
     * 6. Sufficient cash balance for buy orders (ORD-400)
     * 7. Sufficient holdings for sell orders (ORD-409)
     * 8. Idempotency key must be unique (ORD-409)
     *
    * Transactional: validation, order insert, and event registration move together.
     */
    @Transactional
    public Order placeOrder(PlaceOrderRequest request, String idempotencyKey) {
        // Rule 8: idempotency key must be unique
        if (orderMapper.findOrderByIdempotencyKey(idempotencyKey).isPresent()) {
            throw new DuplicateOrderException(idempotencyKey);
        }

        // Validate account exists and is active (rules 1-2)
        Account account = accountService.getAccountByReferenceStrict(request.getAccountId());

        // Validate instrument exists and is tradable (rule 3)
        com.capstone.entity.Instrument instrument = instrumentService.validateAndGetInstrument(request.getSymbol());

        // Validate order parameters (rules 4-5)
        validateOrderParameters(request);

        // Validate sufficient funds/holdings (rules 6-7)
        validateFunds(request, account, instrument.getInstrumentId());

        // Generate order ID from UUID
        UUID orderId = UUID.randomUUID();

        // Create Order entity using immutable constructor
        // Convert quantity from Integer to BigDecimal for schema compatibility
        Order order = new Order(
                orderId,
                account.getAccountId(),
                account.getAccountReference(),
                instrument.getInstrumentId(),  // Use actual instrument ID from database
                request.getSide(),  // Already OrderSide enum
                new BigDecimal(request.getQuantity()),
                request.getPrice(),
                OrderStatus.NEW,
                idempotencyKey,
                Instant.now()
        );

        // Insert order
        orderMapper.insertOrder(order);

        transactionCommitHook.runAfterCommit(() ->
            orderPlacedEventPublisher.publishOrderPlaced(order, request.getSymbol()));

        return order;
    }

    /**
     * Retrieve order by ID.
     */
    public Optional<Order> getOrderById(UUID orderId) {
        return orderMapper.findOrderById(orderId);
    }

    /**
     * Retrieve all orders for a given account.
     */
    public List<Order> getOrdersByAccountId(Long accountId) {
        return orderMapper.findOrdersByAccountId(accountId);
    }

    /**
     * Cancel/delete an order (update status to CANCELLED).
     * Only succeeds if the order is currently in NEW status.
     * Conditional in a single statement to prevent race conditions.
     * 
     * @throws OrderNotCancellableException (ORD-409) if order is not in NEW status
     */
    @Transactional
    public void cancelOrder(UUID orderId) {
        int rowsAffected = orderMapper.updateOrderStatusConditional(
            orderId, 
            OrderStatus.NEW, 
            OrderStatus.CANCELLED
        );
        
        if (rowsAffected == 0) {
            throw new OrderNotCancellableException(orderId);
        }
    }

    /**
     * Check for duplicate order using idempotency key.
     */
    public Optional<Order> getOrderByIdempotencyKey(String idempotencyKey) {
        return orderMapper.findOrderByIdempotencyKey(idempotencyKey);
    }

    /**
     * Validate order parameters (quantity and price).
     */
    private void validateOrderParameters(PlaceOrderRequest request) {
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new InvalidOrderException("quantity");
        }

        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderException("price");
        }
    }

    /**
     * Validate order affordability for BUY and holdings sufficiency for SELL.
     * No cash or holdings mutation happens in trade-api at placement time.
     * 
     * @throws InsufficientFundsException if insufficient funds
     */
    private void validateFunds(PlaceOrderRequest request, Account account, Long instrumentId) {
        OrderSide side = request.getSide();

        if (side == OrderSide.BUY) {
            // Check cash balance for buy orders
            BigDecimal requiredCash = new BigDecimal(request.getQuantity())
                    .multiply(request.getPrice());

            if (account.getCashBalance().compareTo(requiredCash) < 0) {
                throw new InsufficientFundsException(
                    account.getAccountId(),
                        requiredCash,
                        account.getCashBalance()
                );
            }

            return;
        } else if (side == OrderSide.SELL) {
            BigDecimal requiredQuantity = new BigDecimal(request.getQuantity());
            BigDecimal availableQuantity = orderMapper
                    .findHoldingQuantityForUpdate(account.getAccountId(), instrumentId)
                    .orElse(BigDecimal.ZERO);

            if (availableQuantity.compareTo(requiredQuantity) < 0) {
                throw new InsufficientHoldingsException(
                        account.getAccountId(),
                        request.getSymbol(),
                        requiredQuantity,
                        availableQuantity
                );
            }

            return;
        }

        throw new InvalidOrderException("side");
    }

    /**
     * Update order status (used for order execution/completion).
     */
    public void updateOrderStatus(UUID orderId, OrderStatus status) {
        orderMapper.updateOrderStatus(orderId, status);
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

}
