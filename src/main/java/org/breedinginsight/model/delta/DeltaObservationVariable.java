package org.breedinginsight.model.delta;

import org.brapi.v2.model.pheno.BrAPIObservationVariable;

public class DeltaObservationVariable implements DeltaEntity<BrAPIObservationVariable> {

    private BrAPIObservationVariable brAPIObject;

    private BrAPIObservationVariable getBrAPIObject() {
        return null;
    }

    @Override
    public BrAPIObservationVariable cloneBrAPIObject() {
        return DeltaEntity.super.cloneBrAPIObject();
    }
}
