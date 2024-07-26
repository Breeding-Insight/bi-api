package org.breedinginsight.model.delta;

import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.core.BrAPILocation;

@Prototype
public class DeltaLocation extends DeltaEntity<BrAPILocation> {

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaLocation(BrAPILocation brAPIObject) {
        super(brAPIObject);
    }

}
