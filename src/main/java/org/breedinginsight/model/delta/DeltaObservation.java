package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Prototype;
import lombok.Getter;
import lombok.Setter;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;

@Prototype
public class DeltaObservation extends DeltaEntity<BrAPIObservation> {

    @Getter
    @Setter
    private ImportObjectState state;

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaObservation(BrAPIObservation brAPIObject) {
        super(brAPIObject);
    }

}
