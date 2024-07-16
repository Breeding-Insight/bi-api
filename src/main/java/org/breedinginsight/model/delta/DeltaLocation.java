package org.breedinginsight.model.delta;

import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.core.BrAPILocation;

@Prototype
public class DeltaLocation extends DeltaEntity<BrAPILocation> {

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaLocation(BrAPILocation brAPIObject) {
        super(brAPIObject);
    }

    @Override
    public JsonObject getAdditionalInfo() {
        JsonObject additionalInfo = getBrAPIObject().getAdditionalInfo();
        if (additionalInfo == null) {
            additionalInfo = new JsonObject();
        }
        return additionalInfo;
    }

    @Override
    public void setAdditionalInfo(JsonObject additionalInfo) {
        getBrAPIObject().setAdditionalInfo(additionalInfo);
    }

    @Override
    public void putAdditionalInfoItem(String key, Object value) {
        getBrAPIObject().putAdditionalInfoItem(key, value);
    }

}
