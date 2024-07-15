package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.pheno.BrAPIObservation;

@Prototype
public class DeltaObservation extends DeltaEntity<BrAPIObservation> {

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaObservation(BrAPIObservation brAPIObject) {
        super(brAPIObject);
    }

}
