package org.breedinginsight.model.delta;

import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Prototype;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;

@Prototype
public class DeltaObservation extends DeltaEntity<BrAPIObservation> {

    @NonNull
    @Getter
    @Setter
    private ImportObjectState state;

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaObservation(BrAPIObservation brAPIObject) {
        super(brAPIObject);
    }

}
