package com.capstone.exception;

public final class InvalidInputException extends DomainException {

    public InvalidInputException() {
        super("VAL-422", "Invalid input");
    }
}
