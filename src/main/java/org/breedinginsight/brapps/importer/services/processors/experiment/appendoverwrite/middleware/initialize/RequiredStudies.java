package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.initialize;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.StudyService;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Prototype
public class RequiredStudies extends ExpUnitMiddleware {
    StudyService studyService;

    @Inject
    public RequiredStudies(StudyService studyService) {
        this.studyService = studyService;
    }

    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        Program program;
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitByNameNoScope;
        Set<String> studyDbIds;
        List<BrAPIStudy> brAPIStudies;
        List<PendingImportObject<BrAPIStudy>> pendingStudies;
        Map<String, PendingImportObject<BrAPIStudy>> pendingStudyByNameNoScope;

        program = context.getImportContext().getProgram();
        pendingUnitByNameNoScope = context.getPendingData().getObservationUnitByNameNoScope();

        // nothing to do if there are no required units
        if (pendingUnitByNameNoScope.size() == 0) {
            return processNext(context);
        }
        log.debug("fetching from BrAPI service studies belonging to required units");

        // Get the dbIds of the studies belonging to the required exp units
        studyDbIds = pendingUnitByNameNoScope.values().stream().map(studyService::getStudyDbIdBelongingToPendingUnit).collect(Collectors.toSet());

        try {
            // Get the BrAPI studies belonging to required exp units
            brAPIStudies = studyService.fetchBrapiStudiesByDbId(studyDbIds, program);

            // Construct the pending studies from the BrAPI trials
            pendingStudies = brAPIStudies.stream().map(pio -> studyService.constructPIOFromBrapiStudy(pio, program)).collect(Collectors.toList());

            // Construct a hashmap to look up the pending study by study name with the program key removed
            pendingStudyByNameNoScope = pendingStudies.stream().collect(Collectors.toMap(pio -> Utilities.removeProgramKeyAndUnknownAdditionalData(pio.getBrAPIObject().getStudyName(), program.getKey()), pio -> pio));

            // Add the map to the context for use in processing import
            context.getPendingData().setStudyByNameNoScope(pendingStudyByNameNoScope);
        } catch (ApiException e) {
            this.compensate(context);
        }

        return processNext(context);
    }
}
