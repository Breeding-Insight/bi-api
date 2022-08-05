package org.breedinginsight.daos.cache;

import io.lettuce.core.api.StatefulRedisConnection;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.UUID;

@Singleton
public class ProgramCacheProvider {
    private final StatefulRedisConnection<String, String> connection;

    @Inject
    public ProgramCacheProvider(StatefulRedisConnection<String, String> connection) {
        this.connection = connection;
    }

    public <R> ProgramCache<R> getProgramCache(FetchFunction<UUID, Map<String, R>> fetchMethod, Class<R> type) {
        return new ProgramCache<>(connection, fetchMethod, type);
    }
}
