package org.breedinginsight.model.delta;

import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Prototype;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;

@Prototype
public class DeltaObservationUnit extends DeltaEntity<BrAPIObservationUnit> {

    @NonNull
    @Getter
    @Setter
    private ImportObjectState state;

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaObservationUnit(BrAPIObservationUnit brAPIObject) {
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
