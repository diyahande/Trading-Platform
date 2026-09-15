package com.capstone.dto;

import com.capstone.enums.AccountStatus;
import com.capstone.enums.AssetClass;
import com.capstone.enums.OrderSide;
import com.capstone.enums.OrderStatus;
import com.capstone.enums.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DtoAndEnumSmokeTest {

    @Test
    void placeOrderRequest_gettersReturnConstructorValues() {
        PlaceOrderRequest request = new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, 2, new BigDecimal("10.50"), "idem-12345678"
        );

        assertEquals(1L, request.getAccountId());
        assertEquals("AAPL", request.getSymbol());
        assertEquals(OrderSide.BUY, request.getSide());
        assertEquals(2, request.getQuantity());
        assertEquals(new BigDecimal("10.50"), request.getPrice());
        assertEquals("idem-12345678", request.getIdempotencyKey());
    }

    @Test
    void lombokDtos_haveUsableAccessors() {
        LocalDateTime now = LocalDateTime.now();

        AccountResponse accountResponse = new AccountResponse();
        accountResponse.setId(1L);
        accountResponse.setAccountId("acc-1");
        accountResponse.setHolderName("Name");
        accountResponse.setCashBalance(new BigDecimal("10.00"));
        accountResponse.setStatus("ACTIVE");
        accountResponse.setVersion(1);
        accountResponse.setLastUpdated(now);
        assertEquals("acc-1", accountResponse.getAccountId());

        BalanceResponse balanceResponse = new BalanceResponse(1L, new BigDecimal("10.00"), "USD", now);
        assertEquals("USD", balanceResponse.getCurrency());

        ErrorResponse errorResponse = new ErrorResponse("ACC-404", "Account not found");
        assertEquals("ACC-404", errorResponse.getErrorCode());

        OrderHistoryEntry historyEntry = new OrderHistoryEntry("ORD-1", 1L, "AAPL", "BUY", 1,
                new BigDecimal("10"), null, "NEW", "k", now);
        assertEquals("NEW", historyEntry.getStatus());

        OrderResponse orderResponse = new OrderResponse("ORD-2", "NEW", "ok", "AAPL", "BUY", 2, new BigDecimal("20"));
        assertEquals("BUY", orderResponse.getSide());

        PositionResponse positionResponse = new PositionResponse(1L, "AAPL", 2, new BigDecimal("10"));
        assertEquals("AAPL", positionResponse.getSymbol());
    }

    @Test
    void enums_exposeExpectedValues() {
        assertEquals(AccountStatus.ACTIVE, AccountStatus.valueOf("ACTIVE"));
        assertEquals(AssetClass.CRYPTO, AssetClass.valueOf("CRYPTO"));
        assertEquals(OrderSide.SELL, OrderSide.valueOf("SELL"));
        assertEquals(OrderStatus.CANCELLED, OrderStatus.valueOf("CANCELLED"));
        assertEquals(TransactionStatus.FAILED, TransactionStatus.valueOf("FAILED"));
    }
}

