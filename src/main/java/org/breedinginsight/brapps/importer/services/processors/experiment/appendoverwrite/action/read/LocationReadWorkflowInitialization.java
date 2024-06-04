package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingLocation;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.model.ProgramLocation;

@Slf4j
public class LocationReadWorkflowInitialization extends BrAPIReadWorkflowInitialization<ProgramLocation>{
    /**
     * Constructs a new BrAPIReadWorkflowInitialization object with the given ExpUnitMiddlewareContext.
     * Initializes the entity based on the provided context.
     *
     * @param context the ExpUnitMiddlewareContext used for initialization.
     */
    protected LocationReadWorkflowInitialization(ExpUnitMiddlewareContext context) {
        super(context);
    }

    /**
     * Get the BrAPI entity being acted on based on the provided ExpUnitMiddlewareContext.
     *
     * @param context The ExpUnitMiddlewareContext providing information about the entity.
     * @return The ExperimentImportEntity representing the BrAPI entity being acted on.
     */
    @Override
    public ExperimentImportEntity<ProgramLocation> getEntity(ExpUnitMiddlewareContext context) {
        return new PendingLocation(context);
    }
}
