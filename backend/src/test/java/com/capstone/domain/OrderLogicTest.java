package com.capstone.domain;

import com.capstone.domain.DTO.PlaceOrderRequest;
import com.capstone.entity.Account;
import com.capstone.entity.Instrument;
import com.capstone.entity.Order;
import com.capstone.entity.Position;
import com.capstone.enums.AccountStatus;
import com.capstone.enums.OrderSide;
import com.capstone.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
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

    // RULE 1: ACCOUNT MUST EXIST

    @Test
    void shouldRejectWhenAccountDoesNotExist() {
        PlaceOrderRequest request =
                buyRequest(999L, "AAPL", "KEY-0001");

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
        addActiveAccount();
        addInstrument("AAPL");

        assertDoesNotThrow(() ->
                orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0002"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );
    }

    // RULE 2: ACCOUNT MUST BE ACTIVE

    @Test
    void shouldRejectWhenAccountIsSuspended() {
        addAccount(AccountStatus.SUSPENDED);
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
    void shouldRejectWhenAccountIsClosed() {
        addAccount(AccountStatus.CLOSED);
        addInstrument("AAPL");

        AccountNotActiveException exception = assertThrows(
                AccountNotActiveException.class,
                () -> orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0004"),
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
        addActiveAccount();
        addInstrument("AAPL");

        assertDoesNotThrow(() ->
                orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0005"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );
    }

    // RULE 3: INSTRUMENT MUST EXIST AND BE TRADABLE

    @Test
    void shouldRejectWhenInstrumentDoesNotExist() {
        addActiveAccount();

        InstrumentNotFoundException exception = assertThrows(
                InstrumentNotFoundException.class,
                () -> orderLogic.placeOrder(
                        buyRequest(1L, "UNKNOWN", "KEY-0006"),
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
        addActiveAccount();

        Instrument instrument = createInstrument("AAPL");

        instrument.setTradable(false);
        instruments.put("AAPL", instrument);

        InstrumentNotFoundException exception = assertThrows(
                InstrumentNotFoundException.class,
                () -> orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0007"),
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
        addActiveAccount();
        addInstrument("AAPL");

        assertDoesNotThrow(() ->
                orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0008"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );
    }

    // RULE 4: QUANTITY MUST BE GREATER THAN ZERO

    @Test
    void shouldRejectZeroQuantity() {
        addActiveAccount();
        addInstrument("AAPL");

        PlaceOrderRequest request = createRequest(
                1L,
                "AAPL",
                OrderSide.BUY,
                "0",
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
    void shouldRejectNegativeQuantity() {
        addActiveAccount();
        addInstrument("AAPL");

        PlaceOrderRequest request = createRequest(
                1L,
                "AAPL",
                OrderSide.BUY,
                "-1",
                "100.00",
                "KEY-0010"
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
    void shouldAllowPositiveQuantity() {
        addActiveAccount();
        addInstrument("AAPL");

        assertDoesNotThrow(() ->
                orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0011"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );
    }

    // RULE 5: PRICE MUST BE GREATER THAN ZERO

    @Test
    void shouldRejectZeroPrice() {
        addActiveAccount();
        addInstrument("AAPL");

        PlaceOrderRequest request = createRequest(
                1L,
                "AAPL",
                OrderSide.BUY,
                "1",
                "0",
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
    void shouldRejectNegativePrice() {
        addActiveAccount();
        addInstrument("AAPL");

        PlaceOrderRequest request = createRequest(
                1L,
                "AAPL",
                OrderSide.BUY,
                "1",
                "-10.00",
                "KEY-0013"
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
    void shouldAllowPositivePrice() {
        addActiveAccount();
        addInstrument("AAPL");

        assertDoesNotThrow(() ->
                orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0014"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );
    }

    // RULE 6: BUY REQUIRES SUFFICIENT FUNDS

    @Test
    void shouldRejectBuyWhenFundsAreInsufficient() {
        addAccountWithBalance(AccountStatus.ACTIVE, "100.00");
        addInstrument("AAPL");

        PlaceOrderRequest request = createRequest(
                1L,
                "AAPL",
                OrderSide.BUY,
                "2",
                "100.00",
                "KEY-0015"
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
        addAccountWithBalance(AccountStatus.ACTIVE, "200.00");
        addInstrument("AAPL");

        assertDoesNotThrow(() ->
                orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0016"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );
    }

    // RULE 7: SELL REQUIRES SUFFICIENT HOLDINGS

    @Test
    void shouldRejectSellWhenHoldingsAreInsufficient() {
        addActiveAccount();
        addInstrument("AAPL");
        addPosition("2", "100.00");

        PlaceOrderRequest request = createRequest(
                1L,
                "AAPL",
                OrderSide.SELL,
                "3",
                "100.00",
                "KEY-0017"
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
        addActiveAccount();
        addInstrument("AAPL");
        addPosition("2", "100.00");

        assertDoesNotThrow(() ->
                orderLogic.placeOrder(
                        createRequest(
                                1L,
                                "AAPL",
                                OrderSide.SELL,
                                "2",
                                "100.00",
                                "KEY-0018"
                        ),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );
    }

    // RULE 8: IDEMPOTENCY KEY MUST NOT ALREADY BE USED

    @Test
    void shouldRejectDuplicateOrder() {
        addActiveAccount();
        addInstrument("AAPL");

        IdempotencyChecker usedKeyChecker =
                key -> key.equals("KEY-0019");

        DuplicateOrderException exception = assertThrows(
                DuplicateOrderException.class,
                () -> orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0019"),
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
        addActiveAccount();
        addInstrument("AAPL");

        assertDoesNotThrow(() ->
                orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0020"),
                        accounts,
                        instruments,
                        positions,
                        idempotencyChecker
                )
        );
    }

    // EVALUATION ORDER

    @Test
    void accountNotFoundShouldWinBeforeOtherRules() {
        PlaceOrderRequest request =
                buyRequest(999L, "UNKNOWN", "KEY-0021");

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
        addAccountWithBalance(AccountStatus.SUSPENDED, "0.00");
        addInstrument("AAPL");

        DomainException exception = assertThrows(
                AccountNotActiveException.class,
                () -> orderLogic.placeOrder(
                        buyRequest(1L, "AAPL", "KEY-0022"),
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
        addAccountWithBalance(AccountStatus.ACTIVE, "0.00");

        DomainException exception = assertThrows(
                InstrumentNotFoundException.class,
                () -> orderLogic.placeOrder(
                        buyRequest(1L, "UNKNOWN", "KEY-0023"),
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
        addAccountWithBalance(AccountStatus.ACTIVE, "0.00");
        addInstrument("AAPL");

        PlaceOrderRequest request = createRequest(
                1L,
                "AAPL",
                OrderSide.BUY,
                "0",
                "100.00",
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

    @Test
    void invalidPriceShouldWinBeforeInsufficientFunds() {
        addAccountWithBalance(AccountStatus.ACTIVE, "0.00");
        addInstrument("AAPL");

        PlaceOrderRequest request = createRequest(
                1L,
                "AAPL",
                OrderSide.BUY,
                "1",
                "0",
                "KEY-0025"
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

    // SUCCESSFUL BUY

    @Test
    void successfulBuyShouldDebitAccountAndCreatePosition() {
        addActiveAccount();
        addInstrument("AAPL");

        Order order = orderLogic.placeOrder(
                buyRequest(1L, "AAPL", "KEY-0026"),
                accounts,
                instruments,
                positions,
                idempotencyChecker
        );

        Account account = accounts.get(1L);
        Position position = positions.get(positionKey(1L, "AAPL"));

        assertNotNull(order);

        assertEquals(
                new BigDecimal("800.00"),
                account.getCashBalance()
        );

        assertNotNull(position);

        assertEquals(
                new BigDecimal("2"),
                position.getQuantity()
        );

        assertEquals(
                new BigDecimal("100.00"),
                position.getAverageCost()
        );
    }

    // SUCCESSFUL SELL

    @Test
    void successfulSellShouldReducePositionAndCreditAccount() {
        addActiveAccount();
        addInstrument("AAPL");

        addPosition("5", "100.00");

        Order order = orderLogic.placeOrder(
                createRequest(
                        1L,
                        "AAPL",
                        OrderSide.SELL,
                        "2",
                        "120.00",
                        "KEY-0027"
                ),
                accounts,
                instruments,
                positions,
                idempotencyChecker
        );

        Account account = accounts.get(1L);
        Position position = positions.get(positionKey(1L, "AAPL"));

        assertNotNull(order);

        assertEquals(
                new BigDecimal("1240.00"),
                account.getCashBalance()
        );

        assertNotNull(position);

        assertEquals(
                new BigDecimal("3"),
                position.getQuantity()
        );

        // Selling does not change average cost.
        assertEquals(
                new BigDecimal("100.00"),
                position.getAverageCost()
        );
    }

    // TEST DATA HELPERS

    private void addActiveAccount() {
        addAccount(AccountStatus.ACTIVE);
    }

    private void addAccount(AccountStatus status) {
        addAccountWithBalance(status, "1000.00");
    }

    private void addAccountWithBalance(
            AccountStatus status,
            String balance
    ) {
        Account account = new Account(
                1L,
                100L,
                "ACC-001",
                "Alice Morgan",
                "USD",
                new BigDecimal(balance),
                status,
                1L,
                Instant.now()
        );

        accounts.put(1L, account);
    }

    private void addInstrument(String symbol) {
        instruments.put(
                symbol,
                createInstrument(symbol)
        );
    }

    private Instrument createInstrument(String symbol) {
        return new Instrument(
                symbol,
                "Apple Inc.",
                "EQUITY",
                "USD"
        );
    }

    private void addPosition(
            String quantity,
            String averageCost
    ) {
        Position position = new Position(
                1L,
                "AAPL",
                new BigDecimal(quantity),
                new BigDecimal(averageCost)
        );

        positions.put(
                positionKey(1L, "AAPL"),
                position
        );
    }

    private PlaceOrderRequest buyRequest(
            Long accountId,
            String symbol,
            String idempotencyKey
    ) {
        return createRequest(
                accountId,
                symbol,
                OrderSide.BUY,
                "2",
                "100.00",
                idempotencyKey
        );
    }

    private PlaceOrderRequest createRequest(
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

    private String positionKey(
            Long accountId,
            String symbol
    ) {
        return accountId + ":" + symbol;
    }
}