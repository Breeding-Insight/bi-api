package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.pheno.BrAPIObservation;

import static org.breedinginsight.utilities.DatasetUtil.gson;

@Prototype
public class DeltaObservation implements DeltaEntity<BrAPIObservation> {

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaObservation(BrAPIObservation brAPIObject) {
        this.brAPIObject = brAPIObject;
    }

    private final BrAPIObservation brAPIObject;

    private BrAPIObservation getBrAPIObject() {
        return brAPIObject;
    }

    @Override
    public BrAPIObservation cloneBrAPIObject() {
        // Serialize and deserialize to deep copy.
        return gson.fromJson(gson.toJson(getBrAPIObject()), BrAPIObservation.class);
    }
}
