package org.breedinginsight.brapi.v2.dao;

import org.brapi.client.v2.model.exceptions.ApiException;

@FunctionalInterface
public interface FetchFunction<T, R> {
    R apply(T t) throws ApiException;
}
