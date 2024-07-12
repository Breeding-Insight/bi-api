package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.core.BrAPIStudy;

import static org.breedinginsight.utilities.DatasetUtil.gson;

@Prototype
public class Environment implements DeltaEntity<BrAPIStudy> {

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    Environment(BrAPIStudy brAPIObject) {
        this.brAPIObject = brAPIObject;
    }

    private final BrAPIStudy brAPIObject;

    private BrAPIStudy getBrAPIObject() {
        return brAPIObject;
    }

    @Override
    public BrAPIStudy cloneBrAPIObject() {
        // Serialize and deserialize to deep copy.
        return gson.fromJson(gson.toJson(getBrAPIObject()), BrAPIStudy.class);
    }
}
