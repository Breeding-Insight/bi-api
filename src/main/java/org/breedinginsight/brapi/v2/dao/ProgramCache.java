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
import com.rits.cloning.Cloner;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import javax.validation.constraints.NotNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public class ProgramCache<K, R> {

    private final FetchFunction<UUID, Map<K, R>> fetchMethod;
    private final Map<UUID, Semaphore> programSemaphore = new HashMap<>();
    private final Cloner cloner;

    private final Executor executor = Executors.newCachedThreadPool();
    private final LoadingCache<UUID, Map<K, R>> cache = CacheBuilder.newBuilder()
            .build(new CacheLoader<>() {
                @Override
                public Map<K, R> load(@NotNull UUID programId) throws Exception {
                    try {
                        Map<K, R> values = fetchMethod.apply(programId);
                        log.debug("cache loading complete.\nprogramId: " + programId);
                        return values;
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                        cache.invalidate(programId);
                        throw e;
                    }
                }
            });

    public ProgramCache(FetchFunction<UUID, Map<K, R>> fetchMethod, List<UUID> keys) {
        this.fetchMethod = fetchMethod;
        this.cloner = new Cloner();
        // Populate cache on start up
        for (UUID key: keys) {
            updateCache(key);
        }
    }

    public ProgramCache(FetchFunction<UUID, Map<K, R>> fetchMethod) {
        this.fetchMethod = fetchMethod;
        this.cloner = new Cloner();
    }

    public Map<K, R> get(UUID programId) throws ApiException {
        try {
            // This will get current cache data, or wait for the refresh to finish if there is no cache data.
            // TODO: Do we want to wait for a refresh method if it is running? Returns current data right now, even if old
            if (!programSemaphore.containsKey(programId) || cache.getIfPresent(programId) == null) {
                // If the cache is missing, refresh and get
                log.trace("cache miss, fetching from source.\nprogramId: " + programId);
                updateCache(programId);
                Map<K, R> result = new HashMap<>(cache.get(programId));
                result = result.entrySet().stream().map(cloner::deepClone)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                return result;
            } else {
                log.trace("cache contains records for the program.\nprogramId: " + programId);
                // Most cases where the cache is populated
                Map<K, R> result = new HashMap<>(cache.get(programId));
                result = result.entrySet().stream().map(cloner::deepClone)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                return result;
            }
        } catch (ExecutionException e) {
            log.error(e.getMessage(), e);
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

        if (!programSemaphore.get(programId).hasQueuedThreads()) { // if false, an update is already queued, skip this one
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
        }
    }

    //data posted still needs to be list
    public List<R> post(UUID programId, Callable<List<R>> postMethod) throws Exception {
        List<R> response = postMethod.call();
        updateCache(programId); //will make get
        return response;
    }
}
