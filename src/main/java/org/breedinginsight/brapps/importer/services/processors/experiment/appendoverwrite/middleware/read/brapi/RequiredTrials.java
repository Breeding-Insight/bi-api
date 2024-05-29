package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.read.brapi;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read.BrAPITrialReadWorkflowInitialization;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.TrialService;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Prototype
public class RequiredTrials extends ExpUnitMiddleware {
    TrialService trialService;

    @Inject
    public RequiredTrials(TrialService trialService) {
        this.trialService = trialService;
    }

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        // Nothing to do if there are no required units
        if (context.getPendingData().getObservationUnitByNameNoScope().size() == 0) {
            return processNext(context);
        }

        try {
            log.debug("fetching from BrAPI service, trials belonging to required units");
            BrAPITrialReadWorkflowInitialization brAPITrialReadWorkflowInitialization = new BrAPITrialReadWorkflowInitialization(context);
            brAPITrialReadWorkflowInitialization.execute();

        } catch (ApiException e) {
            this.compensate(context, new MiddlewareError(() -> {
                throw new RuntimeException(e);
            }));
        }

        return processNext(context);
    }
}
