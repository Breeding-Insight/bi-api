package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.misc;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.TrialService;

import javax.inject.Inject;

@Slf4j
@Prototype
public class RequiredTrials extends ExpUnitMiddleware {
    TrialService trialService;

    @Inject
    public RequiredTrials(TrialService trialService) {
        this.trialService = trialService;
    }

    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        // Nothing to do if there are no required units
        if (context.getPendingData().getObservationUnitByNameNoScope().size() == 0) {
            return processNext(context);
        }

        try {
            log.debug("fetching from BrAPI service, trials belonging to required units");
            //BrAPITrialReadWorkflowInitialization brAPITrialReadWorkflowInitialization = new BrAPITrialReadWorkflowInitialization(context);
            //brAPITrialReadWorkflowInitialization.execute();
            throw new ApiException();
        } catch (ApiException e) {
            this.compensate(context);
        }

        return processNext(context);
    }
}
