package org.breedinginsight.model.delta;

import org.brapi.v2.model.core.BrAPILocation;

import static org.breedinginsight.utilities.DatasetUtil.gson;

public class DeltaLocation implements DeltaEntity<BrAPILocation> {

    DeltaLocation(BrAPILocation brAPIObject) {
        this.brAPIObject = brAPIObject;
    }

    private final BrAPILocation brAPIObject;

    private BrAPILocation getBrAPIObject() {
        return brAPIObject;
    }

    @Override
    public BrAPILocation cloneBrAPIObject() {
        // Serialize and deserialize to deep copy.
        return gson.fromJson(gson.toJson(getBrAPIObject()), BrAPILocation.class);
    }
}
