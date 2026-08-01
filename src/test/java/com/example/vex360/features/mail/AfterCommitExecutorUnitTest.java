package com.example.vex360.features.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitExecutorUnitTest {

    private AfterCommitExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new AfterCommitExecutor();
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void execute_NoActiveTransaction_RunsImmediately() {
        AtomicInteger counter = new AtomicInteger(0);
        TransactionSynchronizationManager.clearSynchronization();

        executor.execute(counter::incrementAndGet);

        assertEquals(1, counter.get());
    }

    @Test
    void execute_ActiveTransaction_RunsAfterCommit() {
        AtomicInteger counter = new AtomicInteger(0);
        TransactionSynchronizationManager.setActualTransactionActive(true);

        executor.execute(counter::incrementAndGet);

        assertEquals(0, counter.get());

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }

        assertEquals(1, counter.get());
    }

    @Test
    void execute_ActiveTransaction_RolledBack_DoesNotRun() {
        AtomicInteger counter = new AtomicInteger(0);
        TransactionSynchronizationManager.setActualTransactionActive(true);

        executor.execute(counter::incrementAndGet);

        assertEquals(0, counter.get());

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        assertEquals(0, counter.get());
    }
}
