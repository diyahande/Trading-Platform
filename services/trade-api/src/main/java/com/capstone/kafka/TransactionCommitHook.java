package com.capstone.kafka;

public interface TransactionCommitHook {

    void runAfterCommit(Runnable action);
}