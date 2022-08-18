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

package org.breedinginsight.daos.cache;

import com.google.gson.Gson;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.JSON;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.redisson.api.*;

import javax.validation.constraints.NotNull;

import java.util.*;
import java.util.concurrent.*;

/**
 *
 * @param <R> object
 */
@Slf4j
public class ProgramCache<R> {
    private final RedissonClient connection;
    private final Gson gson;
    private final FetchFunction<UUID, Map<String, R>> fetchMethod;
    private final Map<String, RSemaphore> programSemaphore = new HashMap<>();
    private Class<R> type;
    private final Executor executor = Executors.newCachedThreadPool();

    public ProgramCache(RedissonClient connection, FetchFunction<UUID, Map<String, R>> fetchMethod, Class<R> type) {
        this.connection = connection;
        this.gson = new JSON().getGson();
        this.fetchMethod = fetchMethod;
        this.type = type;
    }

    public void populate(List<UUID> keys) {
        for(UUID key : keys) {
            populate(key);
        }
    }

    public void populate(@NotNull UUID key) {
        String cacheKey = generateCacheKey(key);
        String queueKey = cacheKey+":semaphore:queue";
        if (!programSemaphore.containsKey(cacheKey)) {
            RSemaphore semaphore = connection.getSemaphore(cacheKey+":semaphore");
            semaphore.trySetPermits(1);
            programSemaphore.put(cacheKey, semaphore);
        }

        boolean acquired = programSemaphore.get(cacheKey).tryAcquire();

        boolean refresh = true;
        if(!acquired) {
            long queueSize = connection.getAtomicLong(queueKey)
                               .incrementAndGet();
            if(queueSize == 1) {
                try {
                    programSemaphore.get(cacheKey).acquire();
                    log.debug("repopulating cache for " + cacheKey);
                    connection.getAtomicLong(queueKey).set(0);
                } catch (InterruptedException e) {
                    log.error("Error acquiring lock to refresh "+cacheKey, e);
                    throw new RuntimeException(e);
                }
            } else {
                log.debug("A refresh is queued up for key: "+cacheKey+", leaving");
                refresh = false;
            }
        }

        if (refresh) { // if false, an update is already in progress and another one queued up, skip this one
            // Start a refresh process asynchronously
            executor.execute(() -> {
                // Synchronous
                try {
                    log.debug("loading cache for key: " + cacheKey);
                    Map<String, R> values = fetchMethod.apply(key);
                    if(!values.isEmpty()) {
                        log.debug("Caching new values for key: " + cacheKey);
                        Map<String, String> entryMap = new HashMap<>();
                        for (Map.Entry<String, R> val : values.entrySet()) {
                            String entryVal = gson.toJson(val.getValue());
                            entryMap.put(val.getKey(), entryVal);
                        }

                        RMap<String, String> map = connection.getMap(cacheKey);
                        map.clear();
                        map.putAll(entryMap);
                    }
                    log.debug("cache loading complete for key: " + cacheKey);
                } catch (Exception e) {
                    log.error("cache loading error for key: " + cacheKey, e);
                    invalidate(key);
                    throw new InternalServerException(e.getMessage(), e);
                } finally {
                    programSemaphore.get(cacheKey).release();
                }
            });
        }
    }

    public void set(@NotNull UUID key, @NotNull String id, @NotNull R value) {
        connection.getMap(generateCacheKey(key)).put(id, gson.toJson(value));
    }

    public void invalidate(@NotNull UUID key) {
        connection.getMap(generateCacheKey(key)).delete();
    }

    public Map<String, R> get(UUID key) throws ApiException {
        String cacheKey = generateCacheKey(key);
        log.debug("Getting for key: " + cacheKey);

        try {
            if (!connection.getBucket(cacheKey).isExists()) {
                log.debug("cache miss, populating");
                populate(key);
                //block until any updates are done
                programSemaphore.get(cacheKey).acquire();
                programSemaphore.get(cacheKey).release();
            }
            return deserialize(connection.getMap(cacheKey));
        } catch (Exception e) {
            throw new ApiException(e);
        }
    }

    private Map<String, R> deserialize(Map<String, String> cachedVals) {
        Map<String, R> retMap = new HashMap<>();
        cachedVals.forEach((key, value) -> retMap.put(key, gson.fromJson(value, type)));
        return retMap;
    }

    public List<R> post(UUID key, Callable<List<R>> postMethod) {
        log.debug("posting for key: " + generateCacheKey(key));
        List<R> response = null;
        try {
            response = postMethod.call();
            populate(key);
        } catch (Exception e) {
            invalidate(key);
        }
        return response;
    }

    private String generateCacheKey(UUID key) {
        return key.toString() + ":" + type.getSimpleName().toLowerCase();
    }
}
