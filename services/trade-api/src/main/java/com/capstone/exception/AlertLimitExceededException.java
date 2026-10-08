package com.capstone.exception;

public final class AlertLimitExceededException extends DomainException {

    public AlertLimitExceededException(int maxActiveAlerts) {
        super("ALR-409", "Maximum " + maxActiveAlerts + " active alerts per account");
    }
}
