package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.update;

import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingObservation;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

@Slf4j
public class BrAPIObservationUpdate extends BrAPIUpdate<BrAPIObservation> {

    public BrAPIObservationUpdate(ExpUnitMiddlewareContext context) {
        super(context);
    }

    @Override
    public ExperimentImportEntity<BrAPIObservation> getEntity(ExpUnitMiddlewareContext context) {
        return new PendingObservation(context);
    }
}
