package org.breedinginsight.model.delta;

import org.brapi.v2.model.core.BrAPILocation;

public class DeltaLocation implements DeltaEntity<BrAPILocation> {

    private BrAPILocation brAPIObject;

    private BrAPILocation getBrAPIObject() {
        return null;
    }

    @Override
    public BrAPILocation cloneBrAPIObject() {
        return DeltaEntity.super.cloneBrAPIObject();
    }
}
