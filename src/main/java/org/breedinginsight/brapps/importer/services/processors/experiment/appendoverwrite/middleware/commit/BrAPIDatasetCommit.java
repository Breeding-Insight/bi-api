package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create.BrAPICreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create.BrAPIDatasetCreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;

import java.util.Optional;

public class BrAPIDatasetCommit extends ExpUnitMiddleware {
    private BrAPIDatasetCreation datasetCreation;
    private Optional<BrAPICreation.BrAPICreationState> createdDatasets;
    @Override
    public boolean process(ExpUnitMiddlewareContext context) {

        try {
            datasetCreation = new BrAPIDatasetCreation(context);
            createdDatasets = datasetCreation.execute().map(s -> (BrAPICreation.BrAPICreationState) s);
        } catch (ApiException e) {
            this.compensate(context, new MiddlewareError(() -> {
                throw new RuntimeException(e);
            }));
        }
        return processNext(context);
    }

    @Override
    public boolean compensate(ExpUnitMiddlewareContext context, MiddlewareError error) {
        // Tag an error if it occurred in this local transaction
        error.tag(this.getClass().getName());

        // Delete any created datasets
        createdDatasets.ifPresent(BrAPICreation.BrAPICreationState::undo);

        // Undo the prior local transaction
        return compensatePrior(context, error);
    }
}
