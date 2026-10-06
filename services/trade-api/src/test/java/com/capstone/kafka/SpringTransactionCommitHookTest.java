package com.capstone.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringTransactionCommitHookTest {

    @Test
    void runAfterCommit_withoutTransaction_executesImmediately() {
        SpringTransactionCommitHook hook = new SpringTransactionCommitHook();
        AtomicBoolean executed = new AtomicBoolean(false);

        hook.runAfterCommit(() -> executed.set(true));

        assertTrue(executed.get());
    }

    @Test
    void runAfterCommit_withActiveSynchronization_executesOnAfterCommit() {
        SpringTransactionCommitHook hook = new SpringTransactionCommitHook();
        AtomicBoolean executed = new AtomicBoolean(false);

        TransactionSynchronizationManager.initSynchronization();
        try {
            hook.runAfterCommit(() -> executed.set(true));
            assertFalse(executed.get());

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            assertTrue(executed.get());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}