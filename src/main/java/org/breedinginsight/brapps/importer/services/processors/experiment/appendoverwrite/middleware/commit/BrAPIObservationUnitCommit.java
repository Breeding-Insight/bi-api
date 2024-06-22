package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.BrAPICreationFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.WorkflowCreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.MiddlewareError;

import javax.inject.Inject;
import java.util.Optional;

@Slf4j
@Prototype
public class BrAPIObservationUnitCommit extends ExpUnitMiddleware {
    private BrAPICreationFactory brAPICreationFactory;
    private WorkflowCreation<BrAPIObservationUnit> brAPIObservationUnitCreation;
    private Optional<WorkflowCreation.BrAPICreationState> createdBrAPIObservationUnits;

    @Inject
    public BrAPIObservationUnitCommit(BrAPICreationFactory brAPICreationFactory) {
        this.brAPICreationFactory = brAPICreationFactory;
    }
    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        try{
            brAPIObservationUnitCreation = brAPICreationFactory.observationUnitWorkflowCreationBean(context);
            log.info("creating new observation units in the BrAPI service");
            createdBrAPIObservationUnits = brAPIObservationUnitCreation.execute().map(s -> (WorkflowCreation.BrAPICreationState) s);
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
        createdBrAPIObservationUnits.ifPresent(WorkflowCreation.BrAPICreationState::undo);

        // Undo the prior local transaction
        return compensatePrior(context);
    }
}
