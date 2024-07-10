package org.breedinginsight.model.delta;

import org.brapi.v2.model.core.BrAPITrial;

public class Experiment implements DeltaEntity<BrAPITrial> {

    private BrAPITrial brAPIObject;
    
    private BrAPITrial getBrAPIObject() {
        return null;
    }

    @Override
    public BrAPITrial cloneBrAPIObject() {
        return DeltaEntity.super.cloneBrAPIObject();
    }
}
