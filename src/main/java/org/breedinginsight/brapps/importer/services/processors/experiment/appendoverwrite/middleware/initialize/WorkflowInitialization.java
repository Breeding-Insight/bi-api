package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.initialize;

import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read.*;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;

@Slf4j
public class WorkflowInitialization extends ExpUnitMiddleware {
    BrAPIObservationUnitReadWorkflowInitialization brAPIObservationUnitReadWorkflowInitialization;
    BrAPITrialReadWorkflowInitialization brAPITrialReadWorkflowInitialization;
    BrAPIStudyReadWorkflowInitialization brAPIStudyReadWorkflowInitialization;
    LocationReadWorkflowInitialization locationReadWorkflowInitialization;
    BrAPIDatasetReadWorkflowInitialization brAPIDatasetReadWorkflowInitialization;
    BrAPIGermplasmReadWorkflowInitialization brAPIGermplasmReadWorkflowInitialization;

    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        brAPIObservationUnitReadWorkflowInitialization = new BrAPIObservationUnitReadWorkflowInitialization(context);
        brAPITrialReadWorkflowInitialization = new BrAPITrialReadWorkflowInitialization(context);
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
            this.compensate(context, new MiddlewareError(() -> {
                throw new InternalServerException(e.toString(), e);
            }));
        }

        return processNext(context);
    }
}
