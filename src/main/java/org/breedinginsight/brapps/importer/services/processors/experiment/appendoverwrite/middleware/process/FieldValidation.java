package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process;

import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationUnitService;
import org.breedinginsight.model.Program;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class FieldValidation extends ExpUnitMiddleware {
    ObservationUnitService observationUnitService;

    @Inject
    public FieldValidation(ObservationUnitService observationUnitService) {
        this.observationUnitService = observationUnitService;
    }

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        Program program;
        Set<String> expUnitIds;
        List<String> missingIds;
        List<BrAPIObservationUnit> brapiUnits;
        List<PendingImportObject<BrAPIObservationUnit>> pendingUnits;
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitById;
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitByNameNoScope;

        log.debug("fetching required exp units from BrAPI service");
        program = context.getImportContext().getProgram();

        // Collect deltabreed-generated exp unit ids listed in the import
        expUnitIds = context.getExpUnitContext().getReferenceOUIds();
        try {
            // For each id fetch the observation unit from the brapi data store
            brapiUnits = observationUnitService.getObservationUnitsByDbId(new HashSet<>(expUnitIds), program);

            // Construct pending import objects from the units
            pendingUnits = brapiUnits.stream().map(observationUnitService::constructPIOFromBrapiUnit).collect(Collectors.toList());

            // Construct a hashmap to look up the pending unit by ID
            pendingUnitById = observationUnitService.mapPendingUnitById(new ArrayList<>(pendingUnits));

            // Construct a hashmap to look up the pending unit by Study+Unit names with program keys removed
            pendingUnitByNameNoScope = observationUnitService.mapPendingUnitByNameNoScope(new ArrayList<>(pendingUnits), program);

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
