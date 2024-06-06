package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.context.annotation.Prototype;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create.BrAPICreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create.BrAPIObservationCreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.update.BrAPIObservationUpdate;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.update.BrAPIUpdate;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;

import java.util.Optional;

@Slf4j
@Prototype
@NoArgsConstructor
public class BrAPIObservationCommit extends ExpUnitMiddleware {
    private BrAPIObservationCreation brAPIObservationCreation;
    private BrAPIObservationUpdate brAPIObservationUpdate;
    private Optional<BrAPICreation.BrAPICreationState> createdBrAPIObservations;
    private Optional<BrAPIUpdate.BrAPIUpdateState> priorBrAPIObservations;
    private Optional<BrAPIUpdate.BrAPIUpdateState> updatedObservations;

    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        try {
            brAPIObservationCreation = new BrAPIObservationCreation(context);
            createdBrAPIObservations = brAPIObservationCreation.execute().map(s -> (BrAPICreation.BrAPICreationState) s);
            brAPIObservationUpdate = new BrAPIObservationUpdate(context);
            priorBrAPIObservations = brAPIObservationUpdate.getBrAPIState().map(s -> (BrAPIUpdate.BrAPIUpdateState) s);
            updatedObservations = brAPIObservationUpdate.execute().map(s -> (BrAPIUpdate.BrAPIUpdateState) s);

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

        // Delete any created observations from the BrAPI service
        createdBrAPIObservations.ifPresent(BrAPICreation.BrAPICreationState::undo);

        // Revert any changes made to observations in the BrAPI service
        priorBrAPIObservations.ifPresent(BrAPIUpdate.BrAPIUpdateState::restore);

        // Undo the prior local transaction
        return compensatePrior(context, error);
    }
}
