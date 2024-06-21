package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create.BrAPICreationFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create.WorkflowCreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;
import org.breedinginsight.model.ProgramLocation;

import javax.inject.Inject;
import java.util.Optional;

@Slf4j
@Prototype
public class LocationCommit extends ExpUnitMiddleware {
    private BrAPICreationFactory brAPICreationFactory;
    private WorkflowCreation<ProgramLocation> locationCreation;
    private Optional<WorkflowCreation.BrAPICreationState> createdLocations;

    @Inject
    public LocationCommit(BrAPICreationFactory brAPICreationFactory) {
        this.brAPICreationFactory = brAPICreationFactory;
    }
    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        try {
            locationCreation = brAPICreationFactory.locationWorkflowCreationBean(context);
            log.info("creating new locationss in the Deltabreed database");
            createdLocations = locationCreation.execute().map(s -> (WorkflowCreation.BrAPICreationState) s);
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

        // Delete any created locations
        createdLocations.ifPresent(WorkflowCreation.BrAPICreationState::undo);

        // Undo the prior local transaction
        return compensatePrior(context);
    }

}
