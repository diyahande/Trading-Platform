package com.capstone.exception;

public final class AlertResourceNotFoundException extends DomainException {

    public AlertResourceNotFoundException(String message) {
        super("ALR-404", message);
    }
}
