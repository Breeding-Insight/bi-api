package org.breedinginsight.model.delta;

import org.brapi.v2.model.pheno.BrAPIObservation;

import static org.breedinginsight.utilities.DatasetUtil.gson;

public class DeltaObservation implements DeltaEntity<BrAPIObservation> {

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
