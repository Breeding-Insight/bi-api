package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;

@Prototype
public class DeltaObservationVariable extends DeltaEntity<BrAPIObservationVariable> {

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaObservationVariable(BrAPIObservationVariable brAPIObject) {
        super(brAPIObject);
    }

}
