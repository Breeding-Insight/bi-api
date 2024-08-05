package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Prototype;
import org.breedinginsight.model.ProgramLocation;

@Prototype
public class DeltaLocation extends DeltaEntity<ProgramLocation> {

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaLocation(ProgramLocation entity) {
        super(entity);
    }

}
