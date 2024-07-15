package org.breedinginsight.model.delta;

import com.google.gson.Gson;

public abstract class DeltaEntity<T> {

    protected final T brAPIObject;
    private final Gson gson;

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    protected DeltaEntity(T brAPIObject) {
        this.brAPIObject = brAPIObject;
        this.gson = new Gson();
    }

    protected T getBrAPIObject() {
        return brAPIObject;
    }

    public T cloneBrAPIObject() {
        // Serialize and deserialize to deep copy.
        return (T) gson.fromJson(gson.toJson(getBrAPIObject()), brAPIObject.getClass());
    }

}
