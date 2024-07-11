package org.breedinginsight.model.delta;

import org.brapi.v2.model.pheno.BrAPIObservationVariable;

import static org.breedinginsight.utilities.DatasetUtil.gson;

public class DeltaObservationVariable implements DeltaEntity<BrAPIObservationVariable> {

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaObservationVariable(BrAPIObservationVariable brAPIObject) {
        this.brAPIObject = brAPIObject;
    }

    private final BrAPIObservationVariable brAPIObject;

    private BrAPIObservationVariable getBrAPIObject() {
        return brAPIObject;
    }

    @Override
    public BrAPIObservationVariable cloneBrAPIObject() {
        // Serialize and deserialize to deep copy.
        return gson.fromJson(gson.toJson(getBrAPIObject()), BrAPIObservationVariable.class);
    }
}
