package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read;

import org.brapi.v2.model.core.BrAPIStudy;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingStudy;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

public class BrAPIStudyReadWorkflowInitialization extends BrAPIReadWorkflowInitialization<BrAPIStudy> {

    public BrAPIStudyReadWorkflowInitialization(ExpUnitMiddlewareContext context) {
        super(context);
    }

    /**
     * Get the BrAPI entity being acted on based on the provided ExpUnitMiddlewareContext.
     *
     * @param context The ExpUnitMiddlewareContext providing information about the entity.
     * @return The ExperimentImportEntity representing the BrAPI entity being acted on.
     */
    @Override
    public ExperimentImportEntity<BrAPIStudy> getEntity(ExpUnitMiddlewareContext context) {
        return new PendingStudy(context);
    }
}
