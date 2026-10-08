package com.capstone.entity;

import com.capstone.enums.AssetClass;
import com.capstone.enums.OrderSide;
import com.capstone.enums.OrderStatus;
import com.capstone.enums.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TradingEntitiesTest {

    @Test
    void instrument_tradabilityAndDelist() {
        Instrument instrument = new Instrument(1L, "AAPL", "Apple", AssetClass.EQUITY, "USD", false, Instant.now());

        assertFalse(instrument.isDelisted());
        assertTrue(instrument.isTradable());

        instrument.delist();

        assertTrue(instrument.isDelisted());
        assertFalse(instrument.isTradable());
    }

    @Test
    void order_stateTransitions_andGuardrails() {
        Order order = new Order(UUID.randomUUID(), 1L, "acc", 2L, OrderSide.BUY,
                new BigDecimal("2"), new BigDecimal("11.00"), OrderStatus.NEW, "k", Instant.now());

        assertTrue(order.isBuy());
        assertFalse(order.isSell());
        assertEquals(new BigDecimal("22.00"), order.getOrderValue());

        order.markFilled();
        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertThrows(IllegalStateException.class, order::cancel);
    }

    @Test
    void order_rejectAndCancel_whenNew() {
        Order rejectable = new Order(UUID.randomUUID(), 1L, "acc", 2L, OrderSide.SELL,
                new BigDecimal("2"), new BigDecimal("11.00"), OrderStatus.NEW, "k", Instant.now());
        rejectable.reject();
        assertEquals(OrderStatus.REJECTED, rejectable.getStatus());

        Order cancellable = new Order(UUID.randomUUID(), 1L, "acc", 2L, OrderSide.SELL,
                new BigDecimal("2"), new BigDecimal("11.00"), OrderStatus.NEW, "k2", Instant.now());
        cancellable.cancel();
        assertEquals(OrderStatus.CANCELLED, cancellable.getStatus());
    }

    @Test
    void portfolio_addAndFindHolding() {
        Portfolio portfolio = new Portfolio(10L, 1L, new BigDecimal("100"), new BigDecimal("80"), Instant.now());
        PortfolioHolding holding = new PortfolioHolding(3L, 10L, 99L, new BigDecimal("2"), new BigDecimal("12.00"), Instant.now());

        portfolio.addHolding(holding);

        assertEquals(holding, portfolio.getHolding(99L));
        assertNull(portfolio.getHolding(100L));
    }

    @Test
    void portfolioHolding_constructorValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> new PortfolioHolding(1L, 1L, 1L, null, BigDecimal.ONE, Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> new PortfolioHolding(1L, 1L, 1L, new BigDecimal("1"), null, Instant.now()));
    }

    @Test
    void portfolioHolding_increaseQuantity_recalculatesAverage() {
        PortfolioHolding holding = new PortfolioHolding(1L, 1L, 1L,
                new BigDecimal("2"), new BigDecimal("10.00"), Instant.parse("2024-01-01T00:00:00Z"));

        holding.increaseQuantity(new BigDecimal("1"), new BigDecimal("16.00"));

        assertEquals(new BigDecimal("3"), holding.getQuantity());
        assertEquals(new BigDecimal("12.00"), holding.getAveragePrice());
        assertTrue(holding.getLastUpdated().isAfter(Instant.parse("2024-01-01T00:00:00Z")));
    }

    @Test
    void portfolioHolding_increaseQuantity_validationErrors() {
        PortfolioHolding holding = new PortfolioHolding(1L, 1L, 1L,
                new BigDecimal("2"), new BigDecimal("10.00"), Instant.now());

        assertThrows(IllegalArgumentException.class, () -> holding.increaseQuantity(BigDecimal.ZERO, new BigDecimal("10.00")));
        assertThrows(IllegalArgumentException.class, () -> holding.increaseQuantity(new BigDecimal("1"), null));
        assertThrows(IllegalArgumentException.class, () -> holding.increaseQuantity(new BigDecimal("1"), new BigDecimal("-1")));
    }

    @Test
    void portfolioHolding_decreaseQuantity_behaviourAndValidation() {
        PortfolioHolding holding = new PortfolioHolding(1L, 1L, 1L,
                new BigDecimal("3"), new BigDecimal("10.00"), Instant.now());

        holding.decreaseQuantity(new BigDecimal("1"));
        assertEquals(new BigDecimal("2"), holding.getQuantity());

        assertThrows(IllegalArgumentException.class, () -> holding.decreaseQuantity(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> holding.decreaseQuantity(new BigDecimal("3")));
    }

    @Test
    void transaction_gettersAndValue() {
        Instant now = Instant.now();
        UUID orderId = UUID.randomUUID();
        Transaction tx = new Transaction(1L, orderId, 2L, new BigDecimal("3"), new BigDecimal("4.00"), now, TransactionStatus.COMPLETED);

        assertEquals(1L, tx.getTransactionId());
        assertEquals(orderId, tx.getOrderId());
        assertEquals(2L, tx.getInstrumentId());
        assertEquals(new BigDecimal("3"), tx.getQuantity());
        assertEquals(new BigDecimal("4.00"), tx.getPrice());
        assertEquals(now, tx.getExecutedAt());
        assertEquals(TransactionStatus.COMPLETED, tx.getStatus());
        assertEquals(new BigDecimal("12.00"), tx.getTransactionValue());
    }

    @Test
    void user_gettersReturnConstructorValues() {
        Instant now = Instant.now();
        User user = new User(1L, "u-1", "Name", "a@b.com", "99999", now);

        assertEquals(1L, user.getUserId());
        assertEquals("u-1", user.getUserReference());
        assertEquals("Name", user.getName());
        assertEquals("a@b.com", user.getEmail());
        assertEquals("99999", user.getPhoneNo());
        assertEquals(now, user.getCreatedAt());
    }
}

