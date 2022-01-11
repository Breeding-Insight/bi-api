/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.brapi.v2.dao;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Slf4j
public class ProgramCache<R> {

    private FetchFunction<UUID, R> fetchMethod;
    Map<UUID, Semaphore> programSemaphore = new HashMap<>();

    final Executor executor = Executors.newCachedThreadPool();
    private LoadingCache<UUID, R> cache = CacheBuilder.newBuilder()
            .build(new CacheLoader<>() {
                @Override
                public R load(UUID programId) throws Exception {
                    try {
                        return fetchMethod.apply(programId);
                    } catch (Exception e) {
                        log.error(e.getMessage());
                        cache.invalidate(programId);
                        throw e;
                    }
                }
            });

    public ProgramCache(FetchFunction fetchMethod, List<UUID> keys) {
        this.fetchMethod = fetchMethod;
        // Populate cache on start up
        for (UUID key: keys) {
            updateCache(key);
        }
    }

    public ProgramCache(FetchFunction fetchMethod) {
        this.fetchMethod = fetchMethod;
    }

    public R get(UUID programId) throws ApiException {
        try {
            // This will get current cache data, or wait for the refresh to finish if there is no cache data.
            // TODO: Do we want to wait for a refresh method if it is running? Returns current data right now, even if old
            if (!programSemaphore.containsKey(programId)) {
                // For the case where the program cache isn't initialize yet. Could happen when a program is created
                // and the germplasm is queried for the first time.
                updateCache(programId);
                return cache.get(programId);
            } else if (cache.getIfPresent(programId) == null && programSemaphore.get(programId).availablePermits() == 1) {
                // For the case where the cache is invalidated but hasn't been refreshed
                updateCache(programId);
                return cache.get(programId);
            } else {
                // Most cases where the cache is populated
                return cache.get(programId);
            }
        } catch (ExecutionException e) {
            log.error(e.getMessage());
            return fetchMethod.apply(programId);
        }
    }

    /*
        Checks to see whether atleast 1 refresh process is queued up and doesn't make another
        refresh request if there is one queued. The idea here is that if you request to refresh the
        cache state, but there is a refresh already waiting, that waiting refresh will grab the most recent
        state of the cache, so queuing another one will be a waste of threads.
    */
    private void updateCache(UUID programId) {

        if (!programSemaphore.containsKey(programId)) {
            programSemaphore.put(programId, new Semaphore(1));
        }

        if (!programSemaphore.get(programId).hasQueuedThreads()) {
            // Start a refresh process asynchronously
            executor.execute(() -> {
                // Synchronous
                try {
                    programSemaphore.get(programId).acquire();
                    cache.refresh(programId);
                } catch (InterruptedException e) {
                    throw new InternalServerException(e.getMessage(), e);
                } finally {
                    programSemaphore.get(programId).release();
                }
            });
        } else {
            // An update is already queued, skip this one
            return;
        }
    }

    public R post(UUID programId, Callable<R> postMethod) throws Exception {
        R response = postMethod.call();
        updateCache(programId);
        return response;
    }
}
