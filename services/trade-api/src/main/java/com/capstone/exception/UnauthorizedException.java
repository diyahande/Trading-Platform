package com.capstone.exception;

public final class UnauthorizedException extends DomainException {

    public UnauthorizedException() {
        super("AUTH-401", "Unauthorized request");
    }
}

