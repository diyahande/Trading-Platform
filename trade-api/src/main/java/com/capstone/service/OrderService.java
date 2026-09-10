package com.capstone.service;

import com.capstone.dto.PlaceOrderRequest;
import com.capstone.entity.Account;
import com.capstone.entity.Order;
import com.capstone.enums.OrderSide;
import com.capstone.enums.OrderStatus;
import com.capstone.exception.DuplicateOrderException;
import com.capstone.exception.InsufficientFundsException;
import com.capstone.exception.InvalidOrderException;
import com.capstone.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
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
     */
    public Order placeOrder(PlaceOrderRequest request, String idempotencyKey) {
        // Rule 8: idempotency key must be unique
        if (orderMapper.findOrderByIdempotencyKey(idempotencyKey).isPresent()) {
            throw new DuplicateOrderException(idempotencyKey);
        }

        // Validate account exists and is active (rules 1-2)
        Account account = accountService.getAccountByIdStrict(request.getAccountId());

        // Validate instrument exists and is tradable (rule 3)
        com.capstone.entity.Instrument instrument = instrumentService.validateAndGetInstrument(request.getSymbol());

        // Validate order parameters (rules 4-5)
        validateOrderParameters(request);

        // Validate sufficient funds/holdings (rules 6-7)
        validateAndReserveFunds(request, account);

        // Generate order ID from UUID
        Long orderId = UUID.randomUUID().getMostSignificantBits();

        // Create Order entity using immutable constructor
        // Convert quantity from Integer to BigDecimal for schema compatibility
        Order order = new Order(
                orderId,
                request.getAccountId(),
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
        return order;
    }

    /**
     * Retrieve order by ID.
     */
    public Optional<Order> getOrderById(Long orderId) {
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
     */
    public void cancelOrder(Long orderId) {
        orderMapper.updateOrderStatus(orderId, OrderStatus.CANCELLED);
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
     * Validate and reserve funds for the order.
     * For BUY orders: check sufficient cash balance and deduct funds.
     * For SELL orders: check sufficient holdings (TODO: full implementation).
     */
    private void validateAndReserveFunds(PlaceOrderRequest request, Account account) {
        OrderSide side = request.getSide();

        if (side == OrderSide.BUY) {
            // Check cash balance for buy orders
            BigDecimal requiredCash = new BigDecimal(request.getQuantity())
                    .multiply(request.getPrice());

            if (account.getCashBalance().compareTo(requiredCash) < 0) {
                throw new InsufficientFundsException(
                        request.getAccountId(),
                        requiredCash,
                        account.getCashBalance()
                );
            }

            // Reserve cash by deducting from account balance
            BigDecimal newBalance = account.getCashBalance().subtract(requiredCash);
            accountService.updateCashBalance(request.getAccountId(), newBalance);
        } else if (side == OrderSide.SELL) {
            // Check holdings for sell orders
            // TODO: Implement holdings validation via positions
        }
    }

    /**
     * Update order status (used for order execution/completion).
     */
    public void updateOrderStatus(Long orderId, OrderStatus status) {
        orderMapper.updateOrderStatus(orderId, status);
    }
}
