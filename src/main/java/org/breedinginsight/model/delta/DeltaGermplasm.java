package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Prototype;
import lombok.Getter;
import lombok.Setter;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;

@Prototype
public class DeltaGermplasm extends DeltaEntity<BrAPIGermplasm> {

    @Getter
    @Setter
    private ImportObjectState state;

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaGermplasm(BrAPIGermplasm brAPIObject) {
        super(brAPIObject);
    }

}
