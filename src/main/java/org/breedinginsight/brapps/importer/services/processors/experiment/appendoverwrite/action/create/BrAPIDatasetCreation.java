package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create;

import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingDataset;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

@Slf4j
public class BrAPIDatasetCreation extends BrAPICreation<BrAPIListDetails> {
    /**
     * Constructor for BrAPICreation class.
     *
     * @param context the ExpUnitMiddlewareContext object
     */
    protected BrAPIDatasetCreation(ExpUnitMiddlewareContext context) {
        super(context);
    }

    /**
     * Abstract method to get the ExperimentImportEntity based on the ExpUnitMiddlewareContext.
     *
     * @param context the ExpUnitMiddlewareContext object
     * @return the ExperimentImportEntity object
     */
    @Override
    public ExperimentImportEntity<BrAPIListDetails> getEntity(ExpUnitMiddlewareContext context) {
        return new PendingDataset(context);
    }
}
