package org.breedinginsight.services.lock;

import org.breedinginsight.DatabaseTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DistributedLockServiceTest extends DatabaseTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void cleanup() {
        executor.shutdownNow();
    }

    @Test
    void secondLockAttemptTimesOutWhileFirstHolds() throws Exception {
        DistributedLockService lockService = new DistributedLockService(super.getRedisConnection());
        String lockKey = "test-lock-key";

        CountDownLatch firstAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        Future<String> firstCall = executor.submit(() ->
                lockService.withLock(lockKey, Duration.ofMillis(500), Duration.ofSeconds(5), () -> {
                    firstAcquired.countDown();
                    // keep the lock held until signaled
                    releaseFirst.await(2, TimeUnit.SECONDS);
                    return "first";
                })
        );

        assertTrue(firstAcquired.await(1, TimeUnit.SECONDS), "First lock holder did not start in time");

        assertThrows(TimeoutException.class, () ->
                lockService.withLock(lockKey, Duration.ofMillis(100), Duration.ofSeconds(2), () -> "second")
        );

        releaseFirst.countDown();
        assertEquals("first", firstCall.get(2, TimeUnit.SECONDS));

        String afterRelease = lockService.withLock(lockKey, Duration.ofMillis(500), Duration.ofSeconds(2), () -> "after-release");
        assertEquals("after-release", afterRelease);
    }
}
