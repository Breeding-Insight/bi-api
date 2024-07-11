package org.breedinginsight.model.delta;

import org.brapi.v2.model.core.BrAPITrial;

import static org.breedinginsight.utilities.DatasetUtil.gson;

public class Experiment implements DeltaEntity<BrAPITrial> {

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    Experiment(BrAPITrial brAPIObject) {
        this.brAPIObject = brAPIObject;
    }

    private final BrAPITrial brAPIObject;
    
    private BrAPITrial getBrAPIObject() {
        return brAPIObject;
    }

    @Override
    public BrAPITrial cloneBrAPIObject() {
        // Serialize and deserialize to deep copy.
        return gson.fromJson(gson.toJson(getBrAPIObject()), BrAPITrial.class);
    }
}
