package org.breedinginsight.model.delta;

import com.github.filosganga.geogson.gson.GeometryAdapterFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.NonNull;

public abstract class DeltaEntity<T> {

    protected static final Gson gson = new GsonBuilder().registerTypeAdapterFactory(new GeometryAdapterFactory()).create();

    @NonNull
    protected final T entity;

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    protected DeltaEntity(@NonNull T entity) {
        this.entity = entity;
    }

    protected T getEntity() {
        return entity;
    }

    public T cloneEntity() {
        // Serialize and deserialize to deep copy.
        return (T) gson.fromJson(gson.toJson(getEntity()), entity.getClass());
    }

}
