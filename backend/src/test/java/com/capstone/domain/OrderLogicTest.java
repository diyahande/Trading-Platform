package com.capstone.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderLogicTest {

    private Map<Long, Account> accounts;
    private Map<String, Instrument> instruments;
    private Map<String, Position> positions;
    private IdempotencyChecker idempotencyChecker;

    private OrderLogic orderLogic;

    @BeforeEach
    void setUp() {
        accounts = new HashMap<>();
        instruments = new HashMap<>();
        positions = new HashMap<>();

        idempotencyChecker = key -> false;

        orderLogic = new OrderLogic();
    }

    // Rule 1: Account must exist

    @Test
    void shouldRejectWhenAccountDoesNotExist() {
        PlaceOrderRequest request = buyRequest(999L, "AAPL", "KEY-0001");

        AccountNotFoundException exception = assertThrows(
                AccountNotFoundException.class,
                () -> orderLogic.placeOrder(
                        request,
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );

        assertEquals("ACC-404", exception.getCode());
    }

    @Test
    void shouldNotRejectWhenAccountExists() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "1000.00");
        addInstrument("AAPL");

        assertDoesNotThrow(() ->
                orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0001"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );
    }

    // Rule 2: Account must be ACTIVE

    @Test
    void shouldRejectWhenAccountIsSuspended() {
        Account account = new Account(
                1L,
                "ACC-001",
                "Alice Morgan",
                "USD",
                new BigDecimal("1000.00")
        );

        account.setStatus(AccountStatus.SUSPENDED);
        accounts.put(1L, account);
        addInstrument("AAPL");

        AccountNotActiveException exception = assertThrows(
                AccountNotActiveException.class,
                () -> orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0002"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );

        assertEquals("ACC-403", exception.getCode());
    }

    @Test
    void shouldRejectWhenAccountIsClosed() {
        Account account = new Account(
                1L,
                "ACC-001",
                "Alice Morgan",
                "USD",
                new BigDecimal("1000.00")
        );

        account.setStatus(AccountStatus.CLOSED);
        accounts.put(1L, account);
        addInstrument("AAPL");

        AccountNotActiveException exception = assertThrows(
                AccountNotActiveException.class,
                () -> orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0003"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );

        assertEquals("ACC-403", exception.getCode());
    }

    @Test
    void shouldAllowTradingWhenAccountIsActive() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "1000.00");
        addInstrument("AAPL");

        assertDoesNotThrow(() ->
                orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0004"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );
    }

    // Rule 3: Instrument must exist and be tradable

    @Test
    void shouldRejectWhenInstrumentDoesNotExist() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "1000.00");

        InstrumentNotFoundException exception = assertThrows(
                InstrumentNotFoundException.class,
                () -> orderLogic.placeOrder(
                        buyRequest(1L, "UNKNOWN", "KEY-0005"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );

        assertEquals("INS-404", exception.getCode());
    }

    @Test
    void shouldRejectWhenInstrumentIsNotTradable() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "1000.00");

        Instrument instrument = new Instrument(
                "AAPL",
                "Apple Inc.",
                "EQUITY",
                "USD"
        );

        instrument.setTradable(false);
        instruments.put("AAPL", instrument);

        InstrumentNotFoundException exception = assertThrows(
                InstrumentNotFoundException.class,
                () -> orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0006"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );

        assertEquals("INS-404", exception.getCode());
    }

    @Test
    void shouldAllowOrderWhenInstrumentIsTradable() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "1000.00");
        addInstrument("AAPL");

        assertDoesNotThrow(() ->
                orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0007"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );
    }

    // Rule 4: Quantity must be greater than zero

    @Test
    void shouldRejectZeroQuantity() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "1000.00");
        addInstrument("AAPL");

        PlaceOrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "0",
                "100.00",
                "KEY-0008"
        );

        InvalidOrderException exception = assertThrows(
                InvalidOrderException.class,
                () -> orderLogic.placeOrder(
                        request,
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );

        assertEquals("VAL-422", exception.getCode());
    }

    @Test
    void shouldRejectNegativeQuantity() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "1000.00");
        addInstrument("AAPL");

        PlaceOrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "-1",
                "100.00",
                "KEY-0009"
        );

        InvalidOrderException exception = assertThrows(
                InvalidOrderException.class,
                () -> orderLogic.placeOrder(
                        request,
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );

        assertEquals("VAL-422", exception.getCode());
    }

    @Test
    void shouldAcceptPositiveQuantity() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "1000.00");
        addInstrument("AAPL");

        assertDoesNotThrow(() ->
                orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0010"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );
    }

    // Rule 5: Price must be greater than zero

    @Test
    void shouldRejectZeroPrice() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "1000.00");
        addInstrument("AAPL");

        PlaceOrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "1",
                "0",
                "KEY-0011"
        );

        InvalidOrderException exception = assertThrows(
                InvalidOrderException.class,
                () -> orderLogic.placeOrder(
                        request,
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );

        assertEquals("VAL-422", exception.getCode());
    }

    @Test
    void shouldRejectNegativePrice() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "1000.00");
        addInstrument("AAPL");

        PlaceOrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "1",
                "-10.00",
                "KEY-0012"
        );

        InvalidOrderException exception = assertThrows(
                InvalidOrderException.class,
                () -> orderLogic.placeOrder(
                        request,
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );

        assertEquals("VAL-422", exception.getCode());
    }

    @Test
    void shouldAcceptPositivePrice() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "1000.00");
        addInstrument("AAPL");

        assertDoesNotThrow(() ->
                orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0013"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );
    }

    // Rule 6: BUY requires sufficient funds

    @Test
    void shouldRejectBuyWhenFundsAreInsufficient() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "100.00");
        addInstrument("AAPL");

        PlaceOrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "2",
                "100.00",
                "KEY-0014"
        );

        InsufficientFundsException exception = assertThrows(
                InsufficientFundsException.class,
                () -> orderLogic.placeOrder(
                        request,
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );

        assertEquals("ORD-400", exception.getCode());
    }

    @Test
    void shouldAllowBuyWhenFundsExactlyMatchOrderValue() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "200.00");
        addInstrument("AAPL");

        assertDoesNotThrow(() ->
                orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0015"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );
    }

    // Rule 7: SELL requires sufficient holdings

    @Test
    void shouldRejectSellWhenHoldingsAreInsufficient() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "1000.00");
        addInstrument("AAPL");

        addPosition(
                1L,
                "AAPL",
                "2",
                "100.00"
        );

        PlaceOrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.SELL,
                "3",
                "100.00",
                "KEY-0016"
        );

        InsufficientHoldingException exception = assertThrows(
                InsufficientHoldingException.class,
                () -> orderLogic.placeOrder(
                        request,
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );

        assertEquals("ORD-409", exception.getCode());
    }

    @Test
    void shouldAllowSellWhenHoldingsExactlyMatchOrderQuantity() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "1000.00");
        addInstrument("AAPL");

        addPosition(
                1L,
                "AAPL",
                "2",
                "100.00"
        );

        assertDoesNotThrow(() ->
                orderLogic.placeOrder(
                        request(
                                1L,
                                "AAPL",
                                OrderSide.SELL,
                                "2",
                                "100.00",
                                "KEY-0017"
                        ),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );
    }

    // Rule 8: Idempotency key must not already be used

    @Test
    void shouldRejectDuplicateOrder() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "1000.00");
        addInstrument("AAPL");

        IdempotencyChecker usedKeyChecker =
                key -> key.equals("KEY-0018");

        DuplicateOrderException exception = assertThrows(
                DuplicateOrderException.class,
                () -> orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0018"),
                        accounts,
                        instruments,
                        positions,
                        usedKeyChecker
                )
        );

        assertEquals("ORD-409", exception.getCode());
    }

    @Test
    void shouldAllowOrderWithUnusedIdempotencyKey() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "1000.00");
        addInstrument("AAPL");

        assertDoesNotThrow(() ->
                orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0019"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );
    }

    // Evaluation order

    @Test
    void accountNotFoundShouldWinBeforeAnyOtherRule() {
        PlaceOrderRequest request = buyRequest(
                999L,
                "UNKNOWN",
                "KEY-0020"
        );

        DomainException exception = assertThrows(
                AccountNotFoundException.class,
                () -> orderLogic.placeOrder(
                        request,
                        accounts,
                        instruments,
                        positions,
                        key -> true
                )
        );

        assertEquals("ACC-404", exception.getCode());
    }

    @Test
    void accountNotActiveShouldWinBeforeInsufficientFunds() {
        Account account = new Account(
                1L,
                "ACC-001",
                "Alice Morgan",
                "USD",
                new BigDecimal("0.00")
        );

        account.setStatus(AccountStatus.SUSPENDED);
        accounts.put(1L, account);
        addInstrument("AAPL");

        DomainException exception = assertThrows(
                AccountNotActiveException.class,
                () -> orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0021"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );

        assertEquals("ACC-403", exception.getCode());
    }

    @Test
    void instrumentNotFoundShouldWinBeforeInsufficientFunds() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "0.00");

        DomainException exception = assertThrows(
                InstrumentNotFoundException.class,
                () -> orderLogic.placeOrder(
                        buyRequest(1L, "UNKNOWN", "KEY-0022"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );

        assertEquals("INS-404", exception.getCode());
    }

    @Test
    void invalidQuantityShouldWinBeforeInsufficientFunds() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "0.00");
        addInstrument("AAPL");

        PlaceOrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "0",
                "100.00",
                "KEY-0023"
        );

        DomainException exception = assertThrows(
                InvalidOrderException.class,
                () -> orderLogic.placeOrder(
                        request,
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );

        assertEquals("VAL-422", exception.getCode());
    }

    @Test
    void invalidPriceShouldWinBeforeInsufficientFunds() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "0.00");
        addInstrument("AAPL");

        PlaceOrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "1",
                "0",
                "KEY-0024"
        );

        DomainException exception = assertThrows(
                InvalidOrderException.class,
                () -> orderLogic.placeOrder(
                        request,
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );

        assertEquals("VAL-422", exception.getCode());
    }

    // Successful BUY mutation

    @Test
    void successfulBuyShouldDebitAccountAndCreatePosition() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "1000.00");
        addInstrument("AAPL");

        Order order = orderLogic.placeOrder(
                buyRequest(1L, "AAPL", "KEY-0025"),
                accounts,
                instruments,
                positions,
                idempotencyChecker
        );

        Account account = accounts.get(1L);
        Position position = positions.get(positionKey(1L, "AAPL"));

        assertNotNull(order);
        assertEquals(new BigDecimal("800.00"), account.getCashBalance());

        assertNotNull(position);
        assertEquals(new BigDecimal("2"), position.getQuantity());
        assertEquals(new BigDecimal("100.00"), position.getAverageCost());
    }

    // Successful SELL mutation

    @Test
    void successfulSellShouldReducePositionAndCreditAccount() {
        addActiveAccount(1L, "ACC-001", "Alice Morgan", "500.00");
        addInstrument("AAPL");

        addPosition(
                1L,
                "AAPL",
                "5",
                "100.00"
        );

        Order order = orderLogic.placeOrder(
                request(
                        1L,
                        "AAPL",
                        OrderSide.SELL,
                        "2",
                        "120.00",
                        "KEY-0026"
                ),
                accounts,
                instruments,
                positions,
                idempotencyChecker
        );

        Account account = accounts.get(1L);
        Position position = positions.get(positionKey(1L, "AAPL"));

        assertNotNull(order);
        assertEquals(new BigDecimal("740.00"), account.getCashBalance());

        assertNotNull(position);
        assertEquals(new BigDecimal("3"), position.getQuantity());

        // Selling does not change average cost.
        assertEquals(
                new BigDecimal("100.00"),
                position.getAverageCost()
        );
    }

    // Helpers

    private void addActiveAccount(
            Long id,
            String reference,
            String holderName,
            String balance
    ) {
        accounts.put(
                id,
                new Account(
                        id,
                        reference,
                        holderName,
                        "USD",
                        new BigDecimal(balance)
                )
        );
    }

    private void addInstrument(String symbol) {
        Instrument instrument = new Instrument(
                symbol,
                "Apple Inc.",
                "EQUITY",
                "USD"
        );

        instrument.setTradable(true);
        instruments.put(symbol, instrument);
    }

    private void addPosition(
            Long accountId,
            String symbol,
            String quantity,
            String averageCost
    ) {
        Position position = new Position(
                accountId,
                symbol,
                new BigDecimal(quantity),
                new BigDecimal(averageCost)
        );

        positions.put(
                positionKey(accountId, symbol),
                position
        );
    }

    private PlaceOrderRequest buyRequest(
            Long accountId,
            String symbol,
            String idempotencyKey
    ) {
        return request(
                accountId,
                symbol,
                OrderSide.BUY,
                "2",
                "100.00",
                idempotencyKey
        );
    }

    private PlaceOrderRequest request(
            Long accountId,
            String symbol,
            OrderSide side,
            String quantity,
            String price,
            String idempotencyKey
    ) {
        return new PlaceOrderRequest(
                accountId,
                symbol,
                side,
                new BigDecimal(quantity),
                new BigDecimal(price),
                idempotencyKey
        );
    }

    private String positionKey(Long accountId, String symbol) {
        return accountId + ":" + symbol;
    }
}