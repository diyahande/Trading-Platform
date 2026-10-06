package com.capstone.exception;
public class InvalidOrderException extends DomainException {

    private final String field;

    public InvalidOrderException(String field) {
        super(
                "VAL-422",
                "Invalid order"
        );
        this.field = field;
    }

    public String getField() {
        return field;
    }
}