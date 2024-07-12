package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.core.BrAPILocation;

import static org.breedinginsight.utilities.DatasetUtil.gson;

@Prototype
public class DeltaLocation implements DeltaEntity<BrAPILocation> {

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
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
