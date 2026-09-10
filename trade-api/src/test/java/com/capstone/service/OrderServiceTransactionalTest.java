package com.capstone.service;

import com.capstone.dto.PlaceOrderRequest;
import com.capstone.entity.Account;
import com.capstone.entity.Order;
import com.capstone.enums.AccountStatus;
import com.capstone.enums.OrderSide;
import com.capstone.enums.OrderStatus;
import com.capstone.exception.ConcurrentModificationException;
import com.capstone.mapper.OrderMapper;
import com.capstone.mapper.AccountMapper;
import com.capstone.mapper.InstrumentMapper;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderServiceTransactionalTest validates transactional behavior and optimistic locking
 * for order placement, cancellation, and concurrent modification detection.
 *
 * Test scenarios:
 * 1. Place order commits cash and position together
 * 2. Failed order rolls back with no partial write
 * 3. Concurrent update is detected and the second writer is refused (ORD-409)
 * 4. Cancelling a filled order returns ORD-409
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderServiceTransactionalTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private InstrumentService instrumentService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private InstrumentMapper instrumentMapper;

    private Long accountId;
    private String accountRef;
    private String symbol;
    private Long instrumentId;
    private BigDecimal initialBalance;

    @BeforeEach
    @Transactional
    void setUp() {
        // Setup test data
        accountId = 1L;
        accountRef = "TEST-ACC-001";
        symbol = "TESTCO";
        instrumentId = 1L;
        initialBalance = new BigDecimal("100000.00");
    }

    /**
     * Test 1: Place order commits cash and position together.
     * Verifies that:
     * - Cash balance is deducted atomically with order insertion
     * - Transaction succeeds and both writes are visible
     */
    @Test
    @DisplayName("Test 1: Place order commits cash and position together")
    @Transactional
    void testPlaceOrderCommitsCashAndPositionTogether() {
        // Arrange
        Account account = accountMapper.findAccountById(accountId).orElseThrow();
        BigDecimal orderQuantity = new BigDecimal("10");
        BigDecimal orderPrice = new BigDecimal("500.00");
        BigDecimal expectedCashDeduction = orderQuantity.multiply(orderPrice); // 5000.00
        String idempotencyKey = UUID.randomUUID().toString();

        PlaceOrderRequest request = new PlaceOrderRequest(
                accountId,
                symbol,
                OrderSide.BUY,
                10,
                orderPrice,
                idempotencyKey
        );

        // Act
        Order placedOrder = orderService.placeOrder(request, request.getIdempotencyKey());

        // Assert - Verify order was inserted
        assertNotNull(placedOrder.getOrderId());
        assertEquals(OrderStatus.NEW, placedOrder.getStatus());

        // Verify cash was deducted atomically
        Account updatedAccount = accountMapper.findAccountById(accountId).orElseThrow();
        BigDecimal expectedNewBalance = initialBalance.subtract(expectedCashDeduction);
        assertEquals(0, expectedNewBalance.compareTo(updatedAccount.getCashBalance()),
                "Cash balance should be deducted by order amount");

        // Verify order is retrievable
        Optional<Order> retrievedOrder = orderMapper.findOrderById(placedOrder.getOrderId());
        assertTrue(retrievedOrder.isPresent(), "Order should exist after commit");
    }

    /**
     * Test 2: Failed order rolls back with no partial write.
     * Verifies that:
     * - If order insertion fails, cash deduction is rolled back
     * - No partial writes occur
     */
    @Test
    @DisplayName("Test 2: Failed order rolls back with no partial write")
    @Transactional
    void testFailedOrderRollsBackWithNoPartialWrite() {
        // Arrange
        Account account = accountMapper.findAccountById(accountId).orElseThrow();
        BigDecimal initialAccountBalance = account.getCashBalance();
        String idempotencyKey1 = UUID.randomUUID().toString();

        PlaceOrderRequest request = new PlaceOrderRequest(
                accountId,
                symbol,
                OrderSide.BUY,
                10,
                new BigDecimal("500.00"),
                idempotencyKey1
        );

        // Simulate a duplicate idempotency key (should fail on second attempt)
        orderService.placeOrder(request, request.getIdempotencyKey());

        // Act & Assert - Second attempt with same idempotency key should fail
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.placeOrder(request, request.getIdempotencyKey());
        });

        assertTrue(exception.getMessage().contains("ORD-409"),
                "Should throw ORD-409 for duplicate idempotency key");

        // Verify cash was not double-deducted (rollback worked)
        Account rollbackAccount = accountMapper.findAccountById(accountId).orElseThrow();
        // Should only be deducted once, not twice
        BigDecimal singleDeduction = new BigDecimal("5000.00");
        assertEquals(0, initialAccountBalance.subtract(singleDeduction).compareTo(rollbackAccount.getCashBalance()),
                "Cash should only be deducted once despite duplicate attempt");
    }

    /**
     * Test 3: Concurrent update is detected and second writer is refused (ORD-409).
     * Verifies optimistic locking:
     * - Two concurrent writes to the same account are detected
     * - Second writer fails with ORD-409 (ConcurrentModificationException)
     * - No race condition where both reads see same balance and both writes succeed
     */
    @Test
    @DisplayName("Test 3: Concurrent update detected, second writer refused with ORD-409")
    @Transactional
    void testConcurrentModificationDetection() {
        // Arrange - Simulate concurrent reads of the same account
        Account accountVersion1 = accountMapper.findAccountById(accountId).orElseThrow();
        Long initialVersion = accountVersion1.getVersion();

        // First write (succeeds)
        String idempotencyKey1 = UUID.randomUUID().toString();
        PlaceOrderRequest request1 = new PlaceOrderRequest(
                accountId,
                symbol,
                OrderSide.BUY,
                5,
                new BigDecimal("100.00"),
                idempotencyKey1
        );

        Order order1 = orderService.placeOrder(request1, request1.getIdempotencyKey());

        // Verify version was incremented
        Account accountAfterFirstWrite = accountMapper.findAccountById(accountId).orElseThrow();
        assertEquals(initialVersion + 1, accountAfterFirstWrite.getVersion(),
                "Version should be incremented after first write");

        // Simulate second writer using stale version (race condition scenario)
        // This would happen if two requests read the account at the same time
        String idempotencyKey2 = UUID.randomUUID().toString();
        PlaceOrderRequest request2 = new PlaceOrderRequest(
                accountId,
                symbol,
                OrderSide.BUY,
                5,
                new BigDecimal("100.00"),
                idempotencyKey2
        );

        // Try to write using the old version - should fail
        // This is simulated by manually calling updateCashBalance with old version
        BigDecimal newBalance = accountAfterFirstWrite.getCashBalance().subtract(new BigDecimal("500.00"));
        
        ConcurrentModificationException exception = assertThrows(
            ConcurrentModificationException.class,
            () -> accountService.updateCashBalance(accountId, newBalance, initialVersion) // Old version
        );

        assertTrue(exception.getCatalogueCode().equals("ORD-409"),
                "Should throw ORD-409 for concurrent modification");
    }

    /**
     * Test 4: Cancelling a filled order returns ORD-409.
     * Verifies conditional status check:
     * - Order can only be cancelled if status is NEW
     * - Attempting to cancel FILLED order fails with ORD-409
     * - Cancellation uses single conditional statement (no race condition)
     */
    @Test
    @DisplayName("Test 4: Cancelling a filled order returns ORD-409")
    @Transactional
    void testCancellingFilledOrderReturnsORD409() {
        // Arrange - Place an order and manually set it to FILLED
        String idempotencyKey = UUID.randomUUID().toString();
        PlaceOrderRequest request = new PlaceOrderRequest(
                accountId,
                symbol,
                OrderSide.BUY,
                10,
                new BigDecimal("500.00"),
                idempotencyKey
        );

        Order placedOrder = orderService.placeOrder(request, request.getIdempotencyKey());
        Long orderId = placedOrder.getOrderId();

        // Simulate order being filled (by trade executor)
        orderMapper.updateOrderStatusConditional(orderId, OrderStatus.NEW, OrderStatus.FILLED);

        // Act & Assert - Attempt to cancel a filled order
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.cancelOrder(orderId);
        });

        assertTrue(exception.getMessage().contains("ORD-409"),
                "Should throw ORD-409 when cancelling non-NEW order");

        // Verify order status unchanged (still FILLED)
        Order filledOrder = orderMapper.findOrderById(orderId).orElseThrow();
        assertEquals(OrderStatus.FILLED, filledOrder.getStatus(),
                "Order status should remain FILLED after failed cancellation");
    }

    /**
     * Additional test: Successful cancellation of NEW order.
     * Verifies that cancellation succeeds when order is in NEW status.
     */
    @Test
    @DisplayName("Bonus: Successfully cancel a NEW order")
    @Transactional
    void testSuccessfullyCancelNewOrder() {
        // Arrange
        String idempotencyKey = UUID.randomUUID().toString();
        PlaceOrderRequest request = new PlaceOrderRequest(
                accountId,
                symbol,
                OrderSide.BUY,
                10,
                new BigDecimal("500.00"),
                idempotencyKey
        );

        Order placedOrder = orderService.placeOrder(request, request.getIdempotencyKey());
        Long orderId = placedOrder.getOrderId();

        // Act
        orderService.cancelOrder(orderId);

        // Assert
        Order cancelledOrder = orderMapper.findOrderById(orderId).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, cancelledOrder.getStatus(),
                "Order should be CANCELLED after successful cancellation");
    }
}
