package com.capstone.domain;

public interface IdempotencyChecker {
    boolean isUsed(String idempotencyKey);
}
