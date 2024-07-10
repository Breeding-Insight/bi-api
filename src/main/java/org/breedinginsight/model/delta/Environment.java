package org.breedinginsight.model.delta;

import org.brapi.v2.model.core.BrAPIStudy;

public class Environment implements DeltaEntity<BrAPIStudy> {

    private BrAPIStudy brAPIObject;

    private BrAPIStudy getBrAPIObject() {
        return null;
    }

    @Override
    public BrAPIStudy cloneBrAPIObject() {
        return DeltaEntity.super.cloneBrAPIObject();
    }
}
