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
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Slf4j
public class BICache<R> {

    private FetchFunction<UUID, R> fetchMethod;
    Map<UUID, Semaphore> programSemaphore = new HashMap<>();
    Semaphore reloadSemaphore = new Semaphore(1);

    final Executor executor = Executors.newCachedThreadPool();
    private LoadingCache<UUID, R> cache = CacheBuilder.newBuilder()
            .build(new CacheLoader<>() {
                @Override
                public R load(UUID programId) throws Exception {
                    return fetchMethod.apply(programId);
                }

                @Override
                public ListenableFuture<R> reload(UUID programId, R oldValue) throws Exception {
                    System.out.println ("reload by" + Thread.currentThread().getName());
                    ListenableFutureTask<R> task = ListenableFutureTask.create(() -> {
                        try {
                            System.out.println ("finished by" + Thread.currentThread().getName());
                            return fetchMethod.apply(programId);
                        } catch (Exception e) {
                            // If something went wrong with the refresh, invalidate the cache so we aren't left with stale data
                            cache.invalidate(programId);
                            throw e;
                        }
                    });
                    // TODO: Make this synchronous
                    executor.execute(task); // async refresh
                    return task;
                }
            });

    public BICache(FetchFunction fetchMethod, List<UUID> keys) {
        this.fetchMethod = fetchMethod;
        // Populate cache on start up
        for (UUID key: keys) {
            cache.refresh(key);
        }
    }

    public BICache(FetchFunction fetchMethod) {
        this.fetchMethod = fetchMethod;
    }

    public R get(UUID programId) throws ApiException {
        // If cache is loading, it will wait for that to finish
        try {
            return cache.get(programId);
        } catch (ExecutionException e) {
            log.error(e.getMessage());
            return fetchMethod.apply(programId);
        }
    }

    public void updateCache(UUID programId) {
        if (!programSemaphore.containsKey(programId)) {

        } else if (!programSemaphore.get(programId).hasQueuedThreads())
            executor.execute(() -> {
                // Synchronous
                try {
                    programSemaphore.get(programId).acquire();
                    cache.refresh(programId);
                } catch (InterruptedException e) {

                } finally {
                    programSemaphore.get(programId).release();
                }
            });
        }
    }

    private

    public R post(UUID programId, Callable<R> postMethod) throws Exception {
        // TODO: Potential issue.
        //  If a germplasm import has two post groups, parents and children, this will try to start two
        //  refreshes, refresh1 and refresh2. If refresh1 comes back after refresh2, which is possible
        //  because refreshes are async, we will be missing refresh2 from our germplasm list.
        //  I'm not sure that guava accounts for this. If a refresh is already going on with guava the next refresh
        //
        //  If this turns out to be an issue we can generate a hash per refresh and in the reload function
        //
        R response = postMethod.call();
        updateCache(programId);
        return response;
    }
}
