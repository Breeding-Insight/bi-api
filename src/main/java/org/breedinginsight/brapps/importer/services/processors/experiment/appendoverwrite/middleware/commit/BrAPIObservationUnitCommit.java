package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.context.annotation.Prototype;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create.misc.BrAPICreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create.misc.BrAPIObservationUnitCreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;

import java.util.Optional;

@Prototype
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
            context.getExpUnitContext().setProcessError(new MiddlewareError(e));
            return this.compensate(context);
        }
        return processNext(context);
    }

    @Override
    public ExpUnitMiddlewareContext compensate(ExpUnitMiddlewareContext context) {
        // Tag an error if it occurred in this local transaction
        context.getExpUnitContext().getProcessError().tag(this.getClass().getName());

        // Delete any created trials from the BrAPI service
        createdBrAPIObservationUnits.ifPresent(BrAPICreation.BrAPICreationState::undo);

        // Undo the prior local transaction
        return compensatePrior(context);
    }
}
