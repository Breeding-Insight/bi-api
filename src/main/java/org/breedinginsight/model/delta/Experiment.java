package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.core.BrAPITrial;

@Prototype
public class Experiment extends DeltaEntity<BrAPITrial> {

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    Experiment(BrAPITrial brAPIObject) {
        super(brAPIObject);
    }

}
