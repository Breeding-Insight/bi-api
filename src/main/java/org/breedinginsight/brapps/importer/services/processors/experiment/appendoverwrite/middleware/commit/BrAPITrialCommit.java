package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.context.annotation.Prototype;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create.BrAPICreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create.BrAPITrialCreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.update.BrAPITrialUpdate;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.update.BrAPIUpdate;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;

import java.util.Optional;

@Slf4j
@Prototype
@NoArgsConstructor
public class BrAPITrialCommit extends ExpUnitMiddleware {
    private BrAPITrialCreation brAPITrialCreation;
    private BrAPITrialUpdate brAPITrialUpdate;
    private Optional<BrAPICreation.BrAPICreationState> createdBrAPITrials;
    private Optional<BrAPIUpdate.BrAPIUpdateState> priorBrAPITrials;
    private Optional<BrAPIUpdate.BrAPIUpdateState> updatedTrials;

    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        try {
            brAPITrialCreation = new BrAPITrialCreation(context);
            createdBrAPITrials = brAPITrialCreation.execute().map(s -> (BrAPICreation.BrAPICreationState) s);
            brAPITrialUpdate = new BrAPITrialUpdate(context);
            priorBrAPITrials = brAPITrialUpdate.getBrAPIState().map(s -> (BrAPIUpdate.BrAPIUpdateState) s);
            updatedTrials = brAPITrialUpdate.execute().map(s -> (BrAPIUpdate.BrAPIUpdateState) s);

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
        createdBrAPITrials.ifPresent(BrAPICreation.BrAPICreationState::undo);

        // Revert any changes made to trials in the BrAPI service
        priorBrAPITrials.ifPresent(BrAPIUpdate.BrAPIUpdateState::restore);

        // Undo the prior local transaction
        return compensatePrior(context);
    }
}
