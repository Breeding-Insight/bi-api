package org.breedinginsight.model.delta;

import org.brapi.v2.model.pheno.BrAPIObservationUnit;

public class DeltaObservationUnit implements DeltaEntity<BrAPIObservationUnit> {

    private BrAPIObservationUnit brAPIObject;

    private BrAPIObservationUnit getBrAPIObject() {
        return null;
    }

    @Override
    public BrAPIObservationUnit cloneBrAPIObject() {
        return DeltaEntity.super.cloneBrAPIObject();
    }
}
