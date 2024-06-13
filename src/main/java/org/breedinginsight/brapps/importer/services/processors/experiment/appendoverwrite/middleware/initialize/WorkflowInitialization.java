package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.initialize;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read.*;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingEntityFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;

import javax.inject.Inject;

@Slf4j
@Prototype
public class WorkflowInitialization extends ExpUnitMiddleware {
    BrAPIObservationUnitReadWorkflowInitialization brAPIObservationUnitReadWorkflowInitialization;
    WorkflowReadInitialization brAPITrialReadWorkflowInitialization;
    BrAPIStudyReadWorkflowInitialization brAPIStudyReadWorkflowInitialization;
    LocationReadWorkflowInitialization locationReadWorkflowInitialization;
    BrAPIDatasetReadWorkflowInitialization brAPIDatasetReadWorkflowInitialization;
    BrAPIGermplasmReadWorkflowInitialization brAPIGermplasmReadWorkflowInitialization;
    PendingEntityFactory pendingEntityFactory;
    BrAPIReadFactory brAPIReadFactory;

    @Inject
    public WorkflowInitialization(PendingEntityFactory pendingEntityFactory, BrAPIReadFactory brAPIReadFactory) {
        this.pendingEntityFactory = pendingEntityFactory;
        this.brAPIReadFactory = brAPIReadFactory;
    }
    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        brAPIObservationUnitReadWorkflowInitialization = new BrAPIObservationUnitReadWorkflowInitialization(context);
        brAPITrialReadWorkflowInitialization = brAPIReadFactory.trialWorkflowReadInitialization(context);
        brAPIStudyReadWorkflowInitialization = new BrAPIStudyReadWorkflowInitialization(context);
        locationReadWorkflowInitialization = new LocationReadWorkflowInitialization(context);
        brAPIDatasetReadWorkflowInitialization = new BrAPIDatasetReadWorkflowInitialization(context);
        brAPIGermplasmReadWorkflowInitialization = new BrAPIGermplasmReadWorkflowInitialization(context);

        log.debug("reading required BrAPI data from BrAPI service");
        try {
            brAPIObservationUnitReadWorkflowInitialization.execute();
            brAPITrialReadWorkflowInitialization.execute();
            brAPIStudyReadWorkflowInitialization.execute();
            locationReadWorkflowInitialization.execute();
            brAPIDatasetReadWorkflowInitialization.execute();
            brAPIGermplasmReadWorkflowInitialization.execute();
        } catch (ApiException e) {
            context.getExpUnitContext().setProcessError(new MiddlewareError(e));
            return this.compensate(context);
        }

        return processNext(context);
    }
}
