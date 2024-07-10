package org.breedinginsight.model.delta;

import org.brapi.v2.model.germ.BrAPIGermplasm;

public class DeltaGermplasm implements DeltaEntity<BrAPIGermplasm> {

    private BrAPIGermplasm brAPIObject;

    private BrAPIGermplasm getBrAPIObject() {
        return null;
    }

    @Override
    public BrAPIGermplasm cloneBrAPIObject() {
        return DeltaEntity.super.cloneBrAPIObject();
    }
}
