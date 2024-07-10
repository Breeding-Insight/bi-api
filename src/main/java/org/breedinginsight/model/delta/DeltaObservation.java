package org.breedinginsight.model.delta;

import org.brapi.v2.model.pheno.BrAPIObservation;

public class DeltaObservation implements DeltaEntity<BrAPIObservation> {

    private BrAPIObservation brAPIObject;

    private BrAPIObservation getBrAPIObject() {
        return null;
    }

    @Override
    public BrAPIObservation cloneBrAPIObject() {
        return DeltaEntity.super.cloneBrAPIObject();
    }
}
