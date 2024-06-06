package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.read.brapi;

import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.DatasetService;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.GermplasmService;
import org.breedinginsight.model.Program;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class RequiredGermplasm extends ExpUnitMiddleware {
    private final GermplasmService germplasmService;

    @Inject
    public RequiredGermplasm(GermplasmService germplasmService) {

        this.germplasmService = germplasmService;
    }

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        Program program;
        Set<String> germplasmDbIds;
        List<BrAPIGermplasm> brapiGermplasm = null;
        List<PendingImportObject<BrAPIGermplasm>> pendingGermplasm;
        Map<String, PendingImportObject<BrAPIGermplasm>> pendingGermplasmByGID;
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitByNameNoScope;

        program = context.getImportContext().getProgram();
        pendingUnitByNameNoScope = context.getPendingData().getObservationUnitByNameNoScope();

        // nothing to do if there are no observation units
        if (pendingUnitByNameNoScope.size() == 0) {
            return processNext(context);
        }
        log.debug("fetching from BrAPI service, germplasm belonging to required units");

        // Get the dbIds of the germplasm belonging to the required exp units
        germplasmDbIds = pendingUnitByNameNoScope.values().stream().map(ou -> ou.getBrAPIObject().getGermplasmDbId()).collect(Collectors.toSet());
        try {
            // Get the dataset belonging to required exp units
            brapiGermplasm = germplasmService.fetchGermplasmByDbId(new HashSet<>(germplasmDbIds), program);

            // Construct the pending germplasm from the BrAPI locations
            pendingGermplasm = brapiGermplasm.stream().map(germplasmService::constructPIOFromBrapiGermplasm).collect(Collectors.toList());

            // Construct a hashmap to look up the pending germplasm by gid
            pendingGermplasmByGID = pendingGermplasm.stream().collect(Collectors.toMap(germplasmService::getGIDFromGermplasmPIO, pio -> pio));

            // Add the map to the context for use in processing import
            context.getPendingData().setExistingGermplasmByGID(pendingGermplasmByGID);
        } catch (ApiException e) {
            this.compensate(context, new MiddlewareError(() -> {
                throw new RuntimeException(e);
            }));
        }

        return processNext(context);
    }
}
