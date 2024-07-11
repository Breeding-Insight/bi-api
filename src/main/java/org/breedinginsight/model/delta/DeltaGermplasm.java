package org.breedinginsight.model.delta;

import org.brapi.v2.model.germ.BrAPIGermplasm;

import static org.breedinginsight.utilities.DatasetUtil.gson;

public class DeltaGermplasm implements DeltaEntity<BrAPIGermplasm> {

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaGermplasm(BrAPIGermplasm brAPIObject) {
        this.brAPIObject = brAPIObject;
    }

    private final BrAPIGermplasm brAPIObject;

    private BrAPIGermplasm getBrAPIObject() {
        return brAPIObject;
    }

    @Override
    public BrAPIGermplasm cloneBrAPIObject() {
        // Serialize and deserialize to deep copy.
        return gson.fromJson(gson.toJson(getBrAPIObject()), BrAPIGermplasm.class);
    }
}
