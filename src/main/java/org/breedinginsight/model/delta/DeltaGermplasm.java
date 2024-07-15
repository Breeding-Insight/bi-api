package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.germ.BrAPIGermplasm;

@Prototype
public class DeltaGermplasm extends DeltaEntity<BrAPIGermplasm> {

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaGermplasm(BrAPIGermplasm brAPIObject) {
        super(brAPIObject);
    }

}
