package org.breedinginsight.services.lock;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Small helper to provide a consistent pattern for distributed locks across the service layer.
 */
@Slf4j
@Singleton
public class DistributedLockService {

    private final RedissonClient redissonClient;

    @Inject
    public DistributedLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * Execute the given callback guarded by a distributed lock.
     *
     * @param lockKey the key for the distributed lock
     * @param waitTime how long to wait to acquire the lock
     * @param leaseTime how long before the lock automatically releases
     * @param action the work to run while holding the lock
     * @return result of the callback
     * @throws TimeoutException if the lock cannot be acquired within the wait time
     * @throws Exception bubbled up from the callback
     */
    public <T> T withLock(String lockKey, Duration waitTime, Duration leaseTime, Callable<T> action) throws Exception {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new TimeoutException("Unable to acquire lock " + lockKey);
            }
            return action.call();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TimeoutException("Interrupted while acquiring lock " + lockKey);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.warn("Failed to release lock {}", lockKey, e);
                }
            }
        }
    }
}
