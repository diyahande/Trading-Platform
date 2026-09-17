package com.capstone.characterisation.sprint6.orderplacement;

import com.capstone.controller.OrderController;
import com.capstone.entity.Account;
import com.capstone.entity.Instrument;
import com.capstone.entity.Order;
import com.capstone.enums.AccountStatus;
import com.capstone.enums.AssetClass;
import com.capstone.enums.OrderSide;
import com.capstone.enums.OrderStatus;
import com.capstone.exception.AccountNotActiveException;
import com.capstone.exception.GlobalExceptionHandler;
import com.capstone.exception.InsufficientFundsException;
import com.capstone.exception.InstrumentNotFoundException;
import com.capstone.mapper.OrderMapper;
import com.capstone.service.AccountService;
import com.capstone.service.InstrumentService;
import com.capstone.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderPlacementCharacterisationTest {

    private static final String ACTIVE_ACCOUNT = "11111111-1111-4111-8111-111111111111";
    private static final String LOW_CASH_ACCOUNT = "22222222-2222-4222-8222-222222222222";
    private static final String SUSPENDED_ACCOUNT = "33333333-3333-4333-8333-333333333333";

    private OrderMapper orderMapper;
    private AccountService accountService;
    private InstrumentService instrumentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        accountService = mock(AccountService.class);
        instrumentService = mock(InstrumentService.class);

        OrderService orderService = new OrderService(orderMapper, accountService, instrumentService);
        OrderController controller = new OrderController(orderService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void affordableBuy_pinsResponseAndPersistsExpectedState() throws Exception {
        when(accountService.verifyAuthorizedAccount(ACTIVE_ACCOUNT, ACTIVE_ACCOUNT))
                .thenReturn(account(1L, ACTIVE_ACCOUNT, AccountStatus.ACTIVE, new BigDecimal("15000.0000"), 7L));
        when(orderMapper.findOrderByIdempotencyKey("char-idem-0001")).thenReturn(Optional.empty());
        when(accountService.getAccountByReferenceStrict(ACTIVE_ACCOUNT))
                .thenReturn(account(1L, ACTIVE_ACCOUNT, AccountStatus.ACTIVE, new BigDecimal("15000.0000"), 7L));
        when(instrumentService.validateAndGetInstrument("AAPL")).thenReturn(instrument(200L, "AAPL"));

        mockMvc.perform(post("/api/v1/orders")
                        .principal(new UsernamePasswordAuthenticationToken(ACTIVE_ACCOUNT, "n/a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(ACTIVE_ACCOUNT, "AAPL", "BUY", 2, "10.00", "char-idem-0001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.message").value("Order NEW"))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.price").value(10.0))
                .andExpect(jsonPath("$.orderId", startsWith("ORD-")));

        verify(accountService).updateCashBalance(1L, new BigDecimal("14980.0000"), 7L);

        ArgumentCaptor<Order> insertedOrder = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insertOrder(insertedOrder.capture());

        Order order = insertedOrder.getValue();
        assertNotNull(order.getOrderId());
        assertEquals(1L, order.getAccountId());
        assertEquals(ACTIVE_ACCOUNT, order.getAccountReference());
        assertEquals(200L, order.getInstrumentId());
        assertEquals(OrderSide.BUY, order.getSide());
        assertEquals(new BigDecimal("2"), order.getQuantity());
        assertEquals(new BigDecimal("10.00"), order.getPrice());
        assertEquals(OrderStatus.NEW, order.getStatus());
        assertEquals("char-idem-0001", order.getIdempotencyKey());
        assertTrue(order.getCreatedAt().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    void reusedIdempotencyKey_returnsOrd409AndHttp409() throws Exception {
        when(accountService.verifyAuthorizedAccount(ACTIVE_ACCOUNT, ACTIVE_ACCOUNT))
                .thenReturn(account(1L, ACTIVE_ACCOUNT, AccountStatus.ACTIVE, new BigDecimal("15000.0000"), 7L));
        when(orderMapper.findOrderByIdempotencyKey("char-idem-dup"))
                .thenReturn(Optional.of(existingOrder("char-idem-dup")));

        mockMvc.perform(post("/api/v1/orders")
                        .principal(new UsernamePasswordAuthenticationToken(ACTIVE_ACCOUNT, "n/a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(ACTIVE_ACCOUNT, "AAPL", "BUY", 1, "10.00", "char-idem-dup")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORD-409"))
                .andExpect(jsonPath("$.message").value("Duplicate order"));

        verify(accountService, never()).updateCashBalance(any(), any(), any());
    }

    @Test
    void unaffordableBuy_returnsOrd400AndHttp400() throws Exception {
        when(accountService.verifyAuthorizedAccount(LOW_CASH_ACCOUNT, LOW_CASH_ACCOUNT))
                .thenReturn(account(2L, LOW_CASH_ACCOUNT, AccountStatus.ACTIVE, new BigDecimal("25.0000"), 3L));
        when(orderMapper.findOrderByIdempotencyKey("char-idem-no-cash")).thenReturn(Optional.empty());
        when(accountService.getAccountByReferenceStrict(LOW_CASH_ACCOUNT))
                .thenReturn(account(2L, LOW_CASH_ACCOUNT, AccountStatus.ACTIVE, new BigDecimal("25.0000"), 3L));
        when(instrumentService.validateAndGetInstrument("AAPL")).thenReturn(instrument(200L, "AAPL"));

        mockMvc.perform(post("/api/v1/orders")
                        .principal(new UsernamePasswordAuthenticationToken(LOW_CASH_ACCOUNT, "n/a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(LOW_CASH_ACCOUNT, "AAPL", "BUY", 20, "10.00", "char-idem-no-cash")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ORD-400"))
                .andExpect(jsonPath("$.message").value("Insufficient funds"));

        verify(accountService, never()).updateCashBalance(any(), any(), any());
        verify(orderMapper, never()).insertOrder(any());
    }

    @Test
    void unknownSymbol_returnsIns404AndHttp404() throws Exception {
        when(accountService.verifyAuthorizedAccount(ACTIVE_ACCOUNT, ACTIVE_ACCOUNT))
                .thenReturn(account(1L, ACTIVE_ACCOUNT, AccountStatus.ACTIVE, new BigDecimal("15000.0000"), 7L));
        when(orderMapper.findOrderByIdempotencyKey("char-idem-unknown-symbol")).thenReturn(Optional.empty());
        when(accountService.getAccountByReferenceStrict(ACTIVE_ACCOUNT))
                .thenReturn(account(1L, ACTIVE_ACCOUNT, AccountStatus.ACTIVE, new BigDecimal("15000.0000"), 7L));
        when(instrumentService.validateAndGetInstrument("ZZZZ"))
                .thenThrow(new InstrumentNotFoundException("ZZZZ"));

        mockMvc.perform(post("/api/v1/orders")
                        .principal(new UsernamePasswordAuthenticationToken(ACTIVE_ACCOUNT, "n/a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(ACTIVE_ACCOUNT, "ZZZZ", "BUY", 1, "10.00", "char-idem-unknown-symbol")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INS-404"))
                .andExpect(jsonPath("$.message").value("Instrument not found"));

        verify(accountService, never()).updateCashBalance(any(), any(), any());
        verify(orderMapper, never()).insertOrder(any());
    }

    @Test
    void accountNotActive_returnsAcc403AndHttp403() throws Exception {
        when(accountService.verifyAuthorizedAccount(SUSPENDED_ACCOUNT, SUSPENDED_ACCOUNT))
                .thenThrow(new AccountNotActiveException(3L, AccountStatus.SUSPENDED));

        mockMvc.perform(post("/api/v1/orders")
                        .principal(new UsernamePasswordAuthenticationToken(SUSPENDED_ACCOUNT, "n/a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(SUSPENDED_ACCOUNT, "AAPL", "BUY", 1, "10.00", "char-idem-suspended")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"))
                .andExpect(jsonPath("$.message").value("Account not active"));

        verify(orderMapper, never()).findOrderByIdempotencyKey(any());
        verify(accountService, never()).getAccountByReferenceStrict(any());
    }

    private static String orderBody(
            String accountId,
            String symbol,
            String side,
            int quantity,
            String price,
            String idempotencyKey) {
        return """
                {
                  "accountId": "%s",
                  "symbol": "%s",
                  "side": "%s",
                  "quantity": %d,
                  "price": %s,
                  "idempotencyKey": "%s"
                }
                """.formatted(accountId, symbol, side, quantity, price, idempotencyKey);
    }

    private static Account account(
            long accountId,
            String accountReference,
            AccountStatus status,
            BigDecimal cashBalance,
            long version) {
        return new Account(
                accountId,
                1L,
                accountReference,
                "Holder",
                "USD",
                cashBalance,
                status,
                version,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private static Instrument instrument(long instrumentId, String symbol) {
        return new Instrument(
                instrumentId,
                symbol,
                symbol,
                AssetClass.EQUITY,
                "USD",
                false,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private static Order existingOrder(String idempotencyKey) {
        return new Order(
                UUID.randomUUID(),
                1L,
                ACTIVE_ACCOUNT,
                200L,
                OrderSide.BUY,
                new BigDecimal("1"),
                new BigDecimal("10.00"),
                OrderStatus.NEW,
                idempotencyKey,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}