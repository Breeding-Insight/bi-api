package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create;

import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingObservationUnit;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

public class BrAPIObservationUnitCreation extends BrAPICreation<BrAPIObservationUnit> {
    /**
     * Constructor for BrAPICreation class.
     *
     * @param context the ExpUnitMiddlewareContext object
     */
    public BrAPIObservationUnitCreation(ExpUnitMiddlewareContext context) {
        super(context);
    }

    /**
     * Abstract method to get the ExperimentImportEntity based on the ExpUnitMiddlewareContext.
     *
     * @param context the ExpUnitMiddlewareContext object
     * @return the ExperimentImportEntity object
     */
    @Override
    public ExperimentImportEntity<BrAPIObservationUnit> getEntity(ExpUnitMiddlewareContext context) {
        return new PendingObservationUnit(context);
    }
}
