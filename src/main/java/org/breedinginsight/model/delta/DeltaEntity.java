package org.breedinginsight.model.delta;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.UUID;

public abstract class DeltaEntity<T> {

    protected final Gson gson;

    @NonNull
    protected final T brAPIObject;

    @Setter
    @Getter
    protected UUID id;

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

    public abstract JsonObject getAdditionalInfo();

    public abstract void setAdditionalInfo(JsonObject additionalInfo);

    public abstract void putAdditionalInfoItem(String key, Object value);

}
