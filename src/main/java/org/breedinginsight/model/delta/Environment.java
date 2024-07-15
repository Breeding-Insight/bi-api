package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.core.BrAPIStudy;

@Prototype
public class Environment extends DeltaEntity<BrAPIStudy> {

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    Environment(BrAPIStudy brAPIObject) {
        super(brAPIObject);
    }

}
