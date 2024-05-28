package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action;

import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingTrial;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

@Slf4j
public class BrAPITrialUpdate extends BrAPIUpdate<BrAPITrial> {

    public BrAPITrialUpdate(ExpUnitMiddlewareContext context) {
        super(context);
    }

    @Override
    public ExperimentImportEntity<BrAPITrial> getEntity(ExpUnitMiddlewareContext context) {
        return new PendingTrial(context);
    }
}
