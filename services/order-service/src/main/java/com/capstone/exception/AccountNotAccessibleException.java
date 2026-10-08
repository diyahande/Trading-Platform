package com.capstone.exception;

public final class AccountNotAccessibleException extends DomainException {

    public AccountNotAccessibleException() {
        super("ACC-403", "Account not accessible");
    }
}
