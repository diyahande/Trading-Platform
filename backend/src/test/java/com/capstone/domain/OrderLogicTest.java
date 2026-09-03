package com.capstone.domain;

import com.capstone.entity.*;
import com.capstone.enums.*;
import com.capstone.exception.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OrderLogicTest {

    private OrderLogic orderLogic;

    private IdempotencyChecker idempotencyChecker;

    private Account activeAccount;
    private Instrument instrument;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {

        idempotencyChecker = key -> false;

        orderLogic = new OrderLogic(idempotencyChecker);

        activeAccount = createAccount(
                AccountStatus.ACTIVE,
                "1000.00"
        );

        instrument = createInstrument(false);

        portfolio = createPortfolio();
    }


    // RULE 1: ACCOUNT MUST EXIST

    @Test
    void shouldRejectWhenAccountDoesNotExist() {

        Order order = createOrder(
                OrderSide.BUY,
                2,
                "100.00",
                "KEY-001"
        );

        assertThrows(
                AccountNotFoundException.class,
                () -> orderLogic.validate(
                        null,
                        instrument,
                        order,
                        portfolio
                )
        );
    }


    // RULE 2: ACCOUNT MUST BE ACTIVE

    @Test
    void shouldRejectWhenAccountIsSuspended() {

        Account account =
                createAccount(
                        AccountStatus.SUSPENDED,
                        "1000.00"
                );

        Order order = createOrder(
                OrderSide.BUY,
                2,
                "100.00",
                "KEY-002"
        );


        AccountNotActiveException exception =
                assertThrows(
                        AccountNotActiveException.class,
                        () -> orderLogic.validate(
                                account,
                                instrument,
                                order,
                                portfolio
                        )
                );


        assertEquals(
                AccountStatus.SUSPENDED,
                exception.getStatus()
        );
    }


    @Test
    void shouldAllowActiveAccount() {

        Order order = createOrder(
                OrderSide.BUY,
                2,
                "100.00",
                "KEY-003"
        );

        assertDoesNotThrow(() ->
                orderLogic.validate(
                        activeAccount,
                        instrument,
                        order,
                        portfolio
                )
        );
    }



    // RULE 3: INSTRUMENT MUST EXIST AND BE TRADABLE


    @Test
    void shouldRejectWhenInstrumentDoesNotExist() {

        Order order = createOrder(
                OrderSide.BUY,
                2,
                "100.00",
                "KEY-004"
        );


        assertThrows(
                InstrumentNotFoundException.class,
                () -> orderLogic.validate(
                        activeAccount,
                        null,
                        order,
                        portfolio
                )
        );
    }


    @Test
    void shouldRejectWhenInstrumentIsNotTradable() {

        Instrument delistedInstrument =
                createInstrument(true);


        Order order = createOrder(
                OrderSide.BUY,
                2,
                "100.00",
                "KEY-005"
        );


        assertThrows(
                InstrumentNotFoundException.class,
                () -> orderLogic.validate(
                        activeAccount,
                        delistedInstrument,
                        order,
                        portfolio
                )
        );
    }


    @Test
    void shouldAllowTradableInstrument() {

        Order order = createOrder(
                OrderSide.BUY,
                2,
                "100.00",
                "KEY-006"
        );


        assertDoesNotThrow(() ->
                orderLogic.validate(
                        activeAccount,
                        instrument,
                        order,
                        portfolio
                )
        );
    }



    // RULE 4: QUANTITY MUST BE GREATER THAN ZERO


    @Test
    void shouldRejectZeroQuantity() {

        Order order = createOrder(
                OrderSide.BUY,
                0,
                "100.00",
                "KEY-007"
        );


        InvalidOrderException exception =
                assertThrows(
                        InvalidOrderException.class,
                        () -> orderLogic.validate(
                                activeAccount,
                                instrument,
                                order,
                                portfolio
                        )
                );


        assertEquals(
                "quantity",
                exception.getField()
        );
    }


    @Test
    void shouldRejectNegativeQuantity() {

        Order order = createOrder(
                OrderSide.BUY,
                -1,
                "100.00",
                "KEY-008"
        );


        assertThrows(
                InvalidOrderException.class,
                () -> orderLogic.validate(
                        activeAccount,
                        instrument,
                        order,
                        portfolio
                )
        );
    }



    // RULE 5: PRICE MUST BE GREATER THAN ZERO


    @Test
    void shouldRejectZeroPrice() {

        Order order = createOrder(
                OrderSide.BUY,
                1,
                "0",
                "KEY-009"
        );


        InvalidOrderException exception =
                assertThrows(
                        InvalidOrderException.class,
                        () -> orderLogic.validate(
                                activeAccount,
                                instrument,
                                order,
                                portfolio
                        )
                );


        assertEquals(
                "price",
                exception.getField()
        );
    }


    @Test
    void shouldRejectNegativePrice() {

        Order order = createOrder(
                OrderSide.BUY,
                1,
                "-10",
                "KEY-010"
        );


        assertThrows(
                InvalidOrderException.class,
                () -> orderLogic.validate(
                        activeAccount,
                        instrument,
                        order,
                        portfolio
                )
        );
    }



    // RULE 6: BUY REQUIRES SUFFICIENT FUNDS


    @Test
    void shouldRejectBuyWhenFundsAreInsufficient() {


        Account account =
                createAccount(
                        AccountStatus.ACTIVE,
                        "100.00"
                );


        Order order = createOrder(
                OrderSide.BUY,
                2,
                "100.00",
                "KEY-011"
        );


        assertThrows(
                InsufficientFundsException.class,
                () -> orderLogic.validate(
                        account,
                        instrument,
                        order,
                        portfolio
                )
        );
    }


    @Test
    void shouldAllowBuyWhenFundsAreEnough() {

        Order order = createOrder(
                OrderSide.BUY,
                2,
                "100.00",
                "KEY-012"
        );


        assertDoesNotThrow(() ->
                orderLogic.validate(
                        activeAccount,
                        instrument,
                        order,
                        portfolio
                )
        );
    }



    // RULE 7: SELL REQUIRES SUFFICIENT HOLDINGS


    @Test
    void shouldRejectSellWhenHoldingsAreInsufficient() {


        addHolding("2");


        Order order = createOrder(
                OrderSide.SELL,
                3,
                "100.00",
                "KEY-013"
        );


        assertThrows(
                InsufficientHoldingsException.class,
                () -> orderLogic.validate(
                        activeAccount,
                        instrument,
                        order,
                        portfolio
                )
        );
    }



    @Test
    void shouldAllowSellWhenHoldingIsEnough() {


        addHolding("5");


        Order order = createOrder(
                OrderSide.SELL,
                3,
                "100.00",
                "KEY-014"
        );


        assertDoesNotThrow(() ->
                orderLogic.validate(
                        activeAccount,
                        instrument,
                        order,
                        portfolio
                )
        );
    }



    // RULE 8: IDEMPOTENCY


    @Test
    void shouldRejectDuplicateOrder() {


        OrderLogic duplicateChecker =
                new OrderLogic(
                        key -> true
                );


        Order order = createOrder(
                OrderSide.BUY,
                1,
                "100.00",
                "DUPLICATE"
        );


        assertThrows(
                DuplicateOrderException.class,
                () -> duplicateChecker.validate(
                        activeAccount,
                        instrument,
                        order,
                        portfolio
                )
        );
    }



    @Test
    void shouldAllowUnusedIdempotencyKey() {


        Order order = createOrder(
                OrderSide.BUY,
                1,
                "100.00",
                "NEW-KEY"
        );


        assertDoesNotThrow(() ->
                orderLogic.validate(
                        activeAccount,
                        instrument,
                        order,
                        portfolio
                )
        );
    }



    // RULE EVALUATION ORDER


    @Test
    void accountFailureShouldComeBeforeInstrumentFailure() {


        Order order = createOrder(
                OrderSide.BUY,
                0,
                "0",
                "KEY-015"
        );


        assertThrows(
                AccountNotFoundException.class,
                () -> orderLogic.validate(
                        null,
                        null,
                        order,
                        null
                )
        );
    }


    @Test
    void accountStatusShouldComeBeforeFundsCheck() {


        Account suspended =
                createAccount(
                        AccountStatus.SUSPENDED,
                        "0.00"
                );


        Order order = createOrder(
                OrderSide.BUY,
                10,
                "100",
                "KEY-016"
        );


        assertThrows(
                AccountNotActiveException.class,
                () -> orderLogic.validate(
                        suspended,
                        instrument,
                        order,
                        portfolio
                )
        );
    }



    @Test
    void invalidQuantityShouldComeBeforeFundsCheck() {


        Account account =
                createAccount(
                        AccountStatus.ACTIVE,
                        "0.00"
                );


        Order order = createOrder(
                OrderSide.BUY,
                0,
                "100",
                "KEY-017"
        );


        InvalidOrderException exception =
                assertThrows(
                        InvalidOrderException.class,
                        () -> orderLogic.validate(
                                account,
                                instrument,
                                order,
                                portfolio
                        )
                );


        assertEquals(
                "quantity",
                exception.getField()
        );
    }



    // HELPERS


    private Account createAccount(
            AccountStatus status,
            String balance
    ) {

        return new Account(
                1L,
                100L,
                "ACC-001",
                "Alice",
                "USD",
                new BigDecimal(balance),
                status,
                1L,
                Instant.now()
        );
    }



    private Instrument createInstrument(
            boolean delisted
    ) {

        return new Instrument(
                1L,
                "AAPL",
                "Apple Inc",
                AssetClass.EQUITY,
                "USD",
                delisted,
                Instant.now()
        );
    }



    private Order createOrder(
            OrderSide side,
            Integer quantity,
            String price,
            String key
    ) {

        return new Order(
                1L,
                1L,
                "ACC-001",
                1L,
                side,
                quantity,
                new BigDecimal(price),
                OrderStatus.NEW,
                key,
                Instant.now()
        );
    }



    private Portfolio createPortfolio() {

        return new Portfolio(
                1L,
                1L,
                new BigDecimal("0"),
                new BigDecimal("0"),
                Instant.now()
        );
    }



    private void addHolding(
            String quantity
    ) {

        PortfolioHolding holding =
                new PortfolioHolding(
                        1L,
                        1L,
                        1L,
                        new BigDecimal(quantity),
                        new BigDecimal("100.00"),
                        Instant.now()
                );


        portfolio.addHolding(holding);
    }
}