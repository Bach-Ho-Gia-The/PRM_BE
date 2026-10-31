package com.structure.springboot.features.order.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Helper thu lai khi tranh chap khoa.
 *
 * <p>Nhan test de ASCII thuan vi output surefire ma hoa hong tieng Viet co dau.</p>
 */
class TransactionRetryTest {

    /** Thanh cong ngay lan dau: tra ket qua, chi chay dung mot lan. */
    @Test
    void returnsTheResultAndRunsOnceWhenTheWorkSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        String result = TransactionRetry.execute("ok", () -> {
            calls.incrementAndGet();
            return "done";
        });

        assertEquals("done", result);
        assertEquals(1, calls.get());
    }

    /** Thua deadlock mot lan roi qua: phai thu lai va tra ket qua lan hai. */
    @Test
    void retriesAfterADeadlockThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        String result = TransactionRetry.execute("deadlock-once", () -> {
            if (calls.incrementAndGet() == 1) {
                throw new DeadlockLoserDataAccessException("deadlock", new RuntimeException("db"));
            }
            return "recovered";
        });

        assertEquals("recovered", result);
        assertEquals(2, calls.get());
    }

    /** Khoa khong lay duoc mai: bo cuoc sau dung MAX_ATTEMPTS lan va nem lai chinh loi do. */
    @Test
    void givesUpAndRethrowsAfterMaxAttempts() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(CannotAcquireLockException.class, () ->
                TransactionRetry.execute("always-locked", () -> {
                    calls.incrementAndGet();
                    throw new CannotAcquireLockException("still locked");
                }));

        assertEquals(TransactionRetry.MAX_ATTEMPTS, calls.get());
    }

    /**
     * Loi KHONG phai tranh chap khoa bi quan thi nem ngay, khong thu lai.
     *
     * OptimisticLockingFailureException la ca xung dot phien ban — du an co tinh tra ve cho client
     * "refresh roi thu lai", KHONG duoc tu dong thu lai o day.
     */
    @Test
    void doesNotRetryOptimisticLockingOrOtherExceptions() {
        AtomicInteger optimistic = new AtomicInteger();
        assertThrows(OptimisticLockingFailureException.class, () ->
                TransactionRetry.execute("optimistic", () -> {
                    optimistic.incrementAndGet();
                    throw new OptimisticLockingFailureException("version changed");
                }));
        assertEquals(1, optimistic.get());

        AtomicInteger business = new AtomicInteger();
        assertThrows(IllegalStateException.class, () ->
                TransactionRetry.execute("business", () -> {
                    business.incrementAndGet();
                    throw new IllegalStateException("not a lock problem");
                }));
        assertEquals(1, business.get());
    }
}
