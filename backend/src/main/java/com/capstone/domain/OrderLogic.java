package com.capstone.domain;

import com.capstone.entity.*;
import com.capstone.exception.*;

import java.math.BigDecimal;

/**
 * Contains the business rules for placing an order.
 *
 * Rules are deliberately evaluated in order.
 * The first failed rule stops further validation.
 */
public final class OrderLogic {

    private final IdempotencyChecker idempotencyChecker;

    public OrderLogic(IdempotencyChecker idempotencyChecker) {
        this.idempotencyChecker = idempotencyChecker;
    }

    public void validate(
            Account account,
            Instrument instrument,
            Order order,
            Portfolio portfolio) {

        // Rule 1: Account must exist
        if (account == null) {
            throw new AccountNotFoundException(0);
        }

        // Rule 2: Account must be ACTIVE
        if (!account.isActive()) {
            throw new AccountNotActiveException(account.getAccountId(), account.getStatus());
        }

        // Rule 3: Instrument must exist and be tradable
        if (instrument == null) {
            throw new InstrumentNotFoundException("Instrument should not be null");
        }

        if (!instrument.isTradable()) {
            throw new InstrumentNotFoundException(instrument.getSymbol());
        }

        // Rule 4: Quantity must be greater than zero
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            throw new InvalidOrderException("quantity");
        }

        // Rule 5: Price must be greater than zero
        if (order.getLimitPrice() == null
                || order.getLimitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderException("price");
        }

        BigDecimal orderValue = order.getOrderValue();

        // Rule 6: BUY requires sufficient cash
        if (order.isBuy() && !account.canAfford(orderValue)) {
            throw new InsufficientFundsException(
                    account.getAccountId(),
                    orderValue,
                    account.getCashBalance()
            );
        }

        // Rule 7: SELL requires sufficient holdings
        if (order.isSell()) {

            PortfolioHolding holding =
                    portfolio == null
                            ? null
                            : portfolio.getHolding(
                            instrument.getInstrumentId()
                    );

            BigDecimal requestedQuantity =
                    BigDecimal.valueOf(order.getQuantity());

            BigDecimal availableQuantity =
                    holding == null
                            ? BigDecimal.ZERO
                            : holding.getQuantity();

            if (holding == null
                    || availableQuantity.compareTo(requestedQuantity) < 0) {

                throw new InsufficientHoldingsException(
                        account.getAccountId(),
                        instrument.getSymbol(),
                        requestedQuantity,
                        availableQuantity
                );
            }
        }

        // Rule 8: Idempotency key must not already be used
        if (idempotencyChecker.isUsed(order.getIdempotencyKey())) {
            throw new DuplicateOrderException(
                    order.getIdempotencyKey()
            );
        }
    }
}