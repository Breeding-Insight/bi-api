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
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class ProgramCache<R> {

    private FetchFunction<UUID, List<R>> fetchMethod;
    private Map<UUID, Semaphore> programSemaphore = new HashMap<>();

    final Executor executor = Executors.newCachedThreadPool();
    private LoadingCache<UUID, List<R>> cache = CacheBuilder.newBuilder()
            .build(new CacheLoader<>() {
                @Override
                public List<R> load(UUID programId) throws Exception {
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

    public List<R> get(UUID programId) throws ApiException {
        try {
            // This will get current cache data, or wait for the refresh to finish if there is no cache data.
            // TODO: Do we want to wait for a refresh method if it is running? Returns current data right now, even if old
            if (!programSemaphore.containsKey(programId) || cache.getIfPresent(programId) == null) {
                // If the cache is missing, refresh and get
                updateCache(programId);
                return new ArrayList<>(cache.get(programId));
            } else {
                // Most cases where the cache is populated
                return new ArrayList<>(cache.get(programId));
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

    public List<R> post(UUID programId, Callable<List<R>> postMethod) throws Exception {
        List<R> response = postMethod.call();
        updateCache(programId);
        return response;
    }
}
