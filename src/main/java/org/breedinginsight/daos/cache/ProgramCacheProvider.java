package org.breedinginsight.daos.cache;

import org.redisson.api.RedissonClient;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.UUID;

@Singleton
public class ProgramCacheProvider {
    private final RedissonClient connection;

    @Inject
    public ProgramCacheProvider(RedissonClient connection) {
        this.connection = connection;
    }

    public <R> ProgramCache<R> getProgramCache(FetchFunction<UUID, Map<String, R>> fetchMethod, Class<R> type) {
        return new ProgramCache<>(connection, fetchMethod, type);
    }
}
