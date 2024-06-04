package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create;

import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingLocation;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.model.ProgramLocation;

public class LocationCreation extends BrAPICreation<ProgramLocation>{
    /**
     * Constructor for BrAPICreation class.
     *
     * @param context the ExpUnitMiddlewareContext object
     */
    public LocationCreation(ExpUnitMiddlewareContext context) {
        super(context);
    }

    /**
     * Abstract method to get the ExperimentImportEntity based on the ExpUnitMiddlewareContext.
     *
     * @param context the ExpUnitMiddlewareContext object
     * @return the ExperimentImportEntity object
     */
    @Override
    public ExperimentImportEntity<ProgramLocation> getEntity(ExpUnitMiddlewareContext context) {
        return new PendingLocation(context);
    }
}
