package org.breedinginsight.daos.cache;

import org.brapi.client.v2.model.exceptions.ApiException;

@FunctionalInterface
public interface FetchFunction<T, R> {
    R apply(T t) throws ApiException;
}
