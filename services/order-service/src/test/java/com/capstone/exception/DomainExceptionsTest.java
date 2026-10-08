package com.capstone.exception;

import com.capstone.enums.AccountStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DomainExceptionsTest {

    @Test
    void accountNotFound_containsState() {
        AccountNotFoundException ex = new AccountNotFoundException(11L);
        assertEquals("ACC-404", ex.getCatalogueCode());
        assertEquals("Account not found", ex.getMessage());
        assertEquals(11L, ex.getAccountId());
    }

    @Test
    void accountNotActive_containsState() {
        AccountNotActiveException ex = new AccountNotActiveException(2L, AccountStatus.SUSPENDED);
        assertEquals("ACC-403", ex.getCatalogueCode());
        assertEquals(2L, ex.getAccountId());
        assertEquals(AccountStatus.SUSPENDED, ex.getStatus());
    }

    @Test
    void concurrentModification_hasFormattedMessage() {
        ConcurrentModificationException ex = new ConcurrentModificationException(8L, 4L);
        assertEquals("ORD-409", ex.getCatalogueCode());
        assertTrue(ex.getMessage().contains("Account 8"));
        assertTrue(ex.getMessage().contains("version 4"));
    }

    @Test
    void duplicateOrder_containsState() {
        DuplicateOrderException ex = new DuplicateOrderException("idem");
        assertEquals("ORD-409", ex.getCatalogueCode());
        assertEquals("idem", ex.getIdempotencyKey());
    }

    @Test
    void instrumentNotFound_containsState() {
        InstrumentNotFoundException ex = new InstrumentNotFoundException("INFY");
        assertEquals("INS-404", ex.getCatalogueCode());
        assertEquals("INFY", ex.getSymbol());
    }

    @Test
    void insufficientFunds_containsState() {
        InsufficientFundsException ex = new InsufficientFundsException(1L, new BigDecimal("100"), new BigDecimal("25"));
        assertEquals("ORD-400", ex.getCatalogueCode());
        assertEquals(1L, ex.getAccountId());
        assertEquals(new BigDecimal("100"), ex.getRequiredAmount());
        assertEquals(new BigDecimal("25"), ex.getAvailableAmount());
    }

    @Test
    void insufficientHoldings_containsState() {
        InsufficientHoldingsException ex = new InsufficientHoldingsException(
                1L, "AAPL", new BigDecimal("3"), new BigDecimal("1")
        );
        assertEquals("ORD-409", ex.getCatalogueCode());
        assertEquals("AAPL", ex.getSymbol());
        assertEquals(new BigDecimal("3"), ex.getRequestedQuantity());
    }

    @Test
    void invalidOrder_containsField() {
        InvalidOrderException ex = new InvalidOrderException("quantity");
        assertEquals("VAL-422", ex.getCatalogueCode());
        assertEquals("quantity", ex.getField());
    }

    @Test
    void orderNotCancellable_containsOrderId() {
        UUID id = UUID.randomUUID();
        OrderNotCancellableException ex = new OrderNotCancellableException(id);
        assertEquals("ORD-409", ex.getCatalogueCode());
        assertEquals(id, ex.getOrderId());
    }

    @Test
    void orderNotFound_containsOrderId() {
        UUID id = UUID.randomUUID();
        OrderNotFoundException ex = new OrderNotFoundException(id);
        assertEquals("ORD-404", ex.getCatalogueCode());
        assertEquals(id, ex.getOrderId());
    }

    @Test
    void unauthorized_mapsCode() {
        UnauthorizedException ex = new UnauthorizedException();
        assertEquals("AUTH-401", ex.getCatalogueCode());
        assertEquals("Unauthorized request", ex.getMessage());
    }
}

