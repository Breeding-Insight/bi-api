package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read;

import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read.BrAPIReadWorkflowInitialization;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingTrial;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

@Slf4j
public class BrAPITrialReadWorkflowInitialization extends BrAPIReadWorkflowInitialization<BrAPITrial> {

    public BrAPITrialReadWorkflowInitialization(ExpUnitMiddlewareContext context) {
        super(context);
    }

    @Override
    public ExperimentImportEntity<BrAPITrial> getEntity(ExpUnitMiddlewareContext context) {
        return new PendingTrial(context);
    }
}
