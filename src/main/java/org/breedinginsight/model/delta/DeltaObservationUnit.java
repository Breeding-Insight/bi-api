package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;

@Prototype
public class DeltaObservationUnit extends DeltaEntity<BrAPIObservationUnit> {

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaObservationUnit(BrAPIObservationUnit brAPIObject) {
        super(brAPIObject);
    }

}
