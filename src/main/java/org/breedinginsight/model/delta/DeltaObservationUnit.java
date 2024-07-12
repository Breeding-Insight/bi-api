package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;

import static org.breedinginsight.utilities.DatasetUtil.gson;

@Prototype
public class DeltaObservationUnit implements DeltaEntity<BrAPIObservationUnit> {

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaObservationUnit(BrAPIObservationUnit brAPIObject) {
        this.brAPIObject = brAPIObject;
    }

    private final BrAPIObservationUnit brAPIObject;

    private BrAPIObservationUnit getBrAPIObject() {
        return brAPIObject;
    }

    @Override
    public BrAPIObservationUnit cloneBrAPIObject() {
        // Serialize and deserialize to deep copy.
        return gson.fromJson(gson.toJson(getBrAPIObject()), BrAPIObservationUnit.class);    }
}
