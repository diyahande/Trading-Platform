package com.capstone.exception;

public final class MarketDataUnavailableException extends DomainException {

    public MarketDataUnavailableException(String message) {
        super("MKT-503", message);
    }
}