package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.read.brapi;

import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.ExpUnitContextService;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;
import org.breedinginsight.model.Program;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ExistingObservationUnits extends ExpUnitMiddleware {
    ExpUnitContextService expUnitContextService;
    @Inject
    public ExistingObservationUnits(ExpUnitContextService expUnitContextService) {
        this.expUnitContextService = expUnitContextService;
    }

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        Program program;
        Set<String> referenceIds;
        List<String> missingIds;
        List<BrAPIObservationUnit> existingUnits;
        List<PendingImportObject<BrAPIObservationUnit>> pendingExistingUnits;
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitById;
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitByNameNoScope;

        program = context.getImportContext().getProgram();
        try {
            // Collect deltabreed-generated exp unit ids listed in the import
            referenceIds = context.getExpUnitContext().getReferenceOUIds();

            // For each id fetch the observation unit from the brapi data store
            existingUnits = expUnitContextService.getReferenceUnits(new HashSet<>(referenceIds), program);
            if (existingUnits.size() != referenceIds.size()) {

                // Handle case of missing Observation Units in data store
                missingIds = expUnitContextService.collectMissingOUIds(new HashSet<>(referenceIds), new ArrayList<>(existingUnits));
                this.compensate(context, new MiddlewareError(() -> {
                    throw new IllegalStateException("Observation Units not found for ObsUnitId(s): " + String.join(ExpImportProcessConstants.COMMA_DELIMITER, missingIds));
                }));
            }

            // Construct pending import objects from the units
            pendingExistingUnits = existingUnits.stream().map(expUnitContextService::constructPIOFromExistingUnit).collect(Collectors.toList());

            // Construct a hashmap to look up the pending unit by Id
            pendingUnitById = expUnitContextService.mapPendingUnitById(new ArrayList<>(pendingExistingUnits));

            // Construct a hashmap to look up the pending unit by Study+Unit names with program keys removed
            pendingUnitByNameNoScope = expUnitContextService.mapPendingUnitByNameNoScope(new ArrayList<>(pendingExistingUnits), program);

            // add maps to the context for use in processing import
            context.getExpUnitContext().setPendingObsUnitByOUId(pendingUnitById);
            context.getPendingData().setObservationUnitByNameNoScope(pendingUnitByNameNoScope);
        } catch (ApiException e) {
            this.compensate(context, new MiddlewareError(() -> {
                throw new RuntimeException(e);
            }));
        }

        return processNext(context);
    }
}
