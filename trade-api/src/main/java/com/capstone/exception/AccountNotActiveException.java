package com.capstone.exception;
import com.capstone.enums.*;
public final class AccountNotActiveException extends DomainException {

    private final long accountId;
    private final AccountStatus status;

    public AccountNotActiveException(long accountId, AccountStatus status) {
        super("ACC-403", "Account not active");
        this.accountId = accountId;
        this.status = status;
    }

    public long getAccountId() {
        return accountId;
    }

    public AccountStatus getStatus() {
        return status;
    }
}
