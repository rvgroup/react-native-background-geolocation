package com.marianhello.utils;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class RecentLocationKeysTest {
    @Test public void evictsOldEntriesAndCanBeReset() {
        RecentLocationKeys keys = new RecentLocationKeys(2);
        assertTrue(keys.add(1));
        assertFalse(keys.add(1));
        assertTrue(keys.add(2));
        assertTrue(keys.add(3));
        assertTrue(keys.add(1));
        keys.clear();
        assertTrue(keys.add(1));
    }

    @Test public void concurrentDuplicatesAreDeliveredOnce() throws Exception {
        RecentLocationKeys keys = new RecentLocationKeys(10);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger delivered = new AtomicInteger();
        Thread[] threads = new Thread[16];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
                if (keys.add(42)) delivered.incrementAndGet();
            });
            threads[i].start();
        }
        start.countDown();
        for (Thread thread : threads) thread.join(5000);
        assertEquals(1, delivered.get());
    }
}
