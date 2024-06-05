package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read;

import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingDataset;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

public class BrAPIDatasetReadWorkflowInitialization extends BrAPIReadWorkflowInitialization<BrAPIListDetails>{
    /**
     * Constructs a new BrAPIReadWorkflowInitialization object with the given ExpUnitMiddlewareContext.
     * Initializes the entity based on the provided context.
     *
     * @param context the ExpUnitMiddlewareContext used for initialization.
     */
    public BrAPIDatasetReadWorkflowInitialization(ExpUnitMiddlewareContext context) {
        super(context);
    }

    /**
     * Get the BrAPI entity being acted on based on the provided ExpUnitMiddlewareContext.
     *
     * @param context The ExpUnitMiddlewareContext providing information about the entity.
     * @return The ExperimentImportEntity representing the BrAPI entity being acted on.
     */
    @Override
    public ExperimentImportEntity<BrAPIListDetails> getEntity(ExpUnitMiddlewareContext context) {
        return new PendingDataset(context);
    }
}
