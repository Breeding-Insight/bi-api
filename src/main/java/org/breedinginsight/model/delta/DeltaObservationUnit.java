package org.breedinginsight.model.delta;

import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Prototype;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationUnitService;

@Prototype
public class DeltaObservationUnit extends DeltaEntity<BrAPIObservationUnit> {

    @NonNull
    @Getter
    @Setter
    private ImportObjectState state;

    private final ObservationUnitService observationUnitService;

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaObservationUnit(BrAPIObservationUnit brAPIObject, ObservationUnitService observationUnitService) {
        super(brAPIObject);
        this.observationUnitService = observationUnitService;
    }

    public PendingImportObject<BrAPIObservationUnit> constructPIO() {
        return observationUnitService.constructPIOFromBrapiUnit(getBrAPIObject());
    }

}
