package org.breedinginsight.brapi.v2.dao;

import org.breedinginsight.daos.cache.ProgramCache;

import java.util.UUID;

public abstract class BrAPICachedDAO<T> {

    protected ProgramCache<T> programCache;

    public void repopulateCache(UUID programId) {
        // TODO: test calling populate alone (without invalidate first).
        this.programCache.invalidate(programId);
        this.programCache.populate(programId);
    }
}
