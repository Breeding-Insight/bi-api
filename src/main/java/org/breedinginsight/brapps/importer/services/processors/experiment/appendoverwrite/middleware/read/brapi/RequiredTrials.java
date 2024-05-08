package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.read.brapi;

import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.TrialService;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class RequiredTrials extends ExpUnitMiddleware {
    TrialService trialService;

    @Inject
    public RequiredTrials(TrialService trialService) {
        this.trialService = trialService;
    }

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        Program program;
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitByNameNoScope;
        Set<String> trialDbIds;
        List<BrAPITrial> brAPITrials;
        List<PendingImportObject<BrAPITrial>> pendingTrials;
        Map<String, PendingImportObject<BrAPITrial>> pendingTrialByNameNoScope;

        program = context.getImportContext().getProgram();
        pendingUnitByNameNoScope = context.getPendingData().getObservationUnitByNameNoScope();

        // nothing to do if there are no required units
        if (pendingUnitByNameNoScope.size() == 0) {
            return processNext(context);
        }
        log.debug("fetching from BrAPI service trials belonging to required units");

        // Get the dbIds of the trials belonging to the required exp units
        trialDbIds = pendingUnitByNameNoScope.values().stream().map(pendingUnit -> trialService.getTrialDbIdBelongingToPendingUnit(pendingUnit, program)).collect(Collectors.toSet());

        // Get the BrAPI trials belonging to required exp units
        brAPITrials = trialDbIds.stream().map(dbId -> trialService.fetchBrapiTrialBelongingToUnit(dbId, program)).collect(Collectors.toList());

        // Construct the pending trials from the BrAPI trials
        pendingTrials = brAPITrials.stream().map(trialService::constructPIOFromBrapiTrial).collect(Collectors.toList());

        // Construct a hashmap to look up the pending trial by trial name with the program key removed
        pendingTrialByNameNoScope = pendingTrials.stream().collect(Collectors.toMap(pio -> Utilities.removeProgramKey(pio.getBrAPIObject().getTrialName(), program.getKey()), pio -> pio));

        // Add the map to the context for use in processing import
        context.getPendingData().setTrialByNameNoScope(pendingTrialByNameNoScope);

        return processNext(context);
    }
}
