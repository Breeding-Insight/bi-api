package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.services.processors.experiment.factory.action.BrAPICreationFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.factory.action.WorkflowCreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.factory.action.BrAPIUpdateFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.factory.action.WorkflowUpdate;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;

import javax.inject.Inject;
import java.util.Optional;

@Slf4j
@Prototype
public class BrAPIObservationCommit extends ExpUnitMiddleware {
    private final BrAPICreationFactory brAPICreationFactory;
    private final BrAPIUpdateFactory brAPIUpdateFactory;
    private WorkflowCreation<BrAPIObservation> brAPIObservationCreation;
    private WorkflowUpdate<BrAPIObservation> brAPIObservationUpdate;
    private Optional<WorkflowCreation.BrAPICreationState> createdBrAPIObservations;
    private Optional<WorkflowUpdate.BrAPIUpdateState> priorBrAPIObservations;
    private Optional<WorkflowUpdate.BrAPIUpdateState> updatedObservations;

    @Inject
    public BrAPIObservationCommit(BrAPICreationFactory brAPICreationFactory, BrAPIUpdateFactory brAPIUpdateFactory) {
        this.brAPICreationFactory = brAPICreationFactory;
        this.brAPIUpdateFactory = brAPIUpdateFactory;
    }
    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        try {
            brAPIObservationCreation = brAPICreationFactory.observationWorkflowCreationBean(context);
            log.info("creating new observations in the BrAPI service");
            createdBrAPIObservations = brAPIObservationCreation.execute().map(s -> (WorkflowCreation.BrAPICreationState) s);
            brAPIObservationUpdate = brAPIUpdateFactory.observationWorkflowUpdateBean(context);
            priorBrAPIObservations = brAPIObservationUpdate.getBrAPIState().map(s -> s);
            log.info("updating existing observations in the BrAPI service");
            updatedObservations = brAPIObservationUpdate.execute().map(s -> (WorkflowUpdate.BrAPIUpdateState) s);

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

        // Delete any created observations from the BrAPI service
        createdBrAPIObservations.ifPresent(WorkflowCreation.BrAPICreationState::undo);

        // Revert any changes made to observations in the BrAPI service
        priorBrAPIObservations.ifPresent(WorkflowUpdate.BrAPIUpdateState::restore);

        // Undo the prior local transaction
        return compensatePrior(context);
    }
}
