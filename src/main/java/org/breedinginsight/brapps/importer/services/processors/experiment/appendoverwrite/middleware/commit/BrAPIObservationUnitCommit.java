package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create.BrAPICreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create.BrAPIObservationUnitCreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;

import java.util.Optional;

public class BrAPIObservationUnitCommit extends ExpUnitMiddleware {
    private BrAPIObservationUnitCreation brAPIObservationUnitCreation;
    private Optional<BrAPICreation.BrAPICreationState> createdBrAPIObservationUnits;

    /**
     * Subclasses will implement this local transaction.
     *
     * @param context
     */
    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        try{
            brAPIObservationUnitCreation = new BrAPIObservationUnitCreation(context);
            createdBrAPIObservationUnits = brAPIObservationUnitCreation.execute().map(s -> (BrAPICreation.BrAPICreationState) s);
        } catch (ApiException e) {
            this.compensate(context, new MiddlewareError(() -> {
                throw new RuntimeException(e);
            }));
        }
        return processNext(context);
    }

    @Override
    public ExpUnitMiddlewareContext compensate(ExpUnitMiddlewareContext context, MiddlewareError error) {
        // Tag an error if it occurred in this local transaction
        error.tag(this.getClass().getName());

        // Delete any created trials from the BrAPI service
        createdBrAPIObservationUnits.ifPresent(BrAPICreation.BrAPICreationState::undo);

        // Undo the prior local transaction
        return compensatePrior(context, error);
    }
}
