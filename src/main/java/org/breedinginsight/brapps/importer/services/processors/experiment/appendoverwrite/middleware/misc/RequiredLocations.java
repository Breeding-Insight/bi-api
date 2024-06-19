package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.misc;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIStudy;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.LocationService;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramLocation;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Prototype
public class RequiredLocations extends ExpUnitMiddleware {
    LocationService locationService;

    @Inject
    public RequiredLocations(LocationService locationService) {
        this.locationService = locationService;
    }

    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        Program program;
        Set<String> locationDbIds;
        List<ProgramLocation> brapiLocations;
        List<PendingImportObject<ProgramLocation>> pendingLocations;
        Map<String, PendingImportObject<BrAPIStudy>> pendingStudyByNameNoScope;
        Map<String, PendingImportObject<ProgramLocation>> pendingLocationByName;

        program = context.getImportContext().getProgram();
        pendingStudyByNameNoScope = context.getPendingData().getStudyByNameNoScope();

        // nothing to do if there are no required units
        if (pendingStudyByNameNoScope.size() == 0) {
            return processNext(context);
        }
        log.debug("fetching from BrAPI service, locations belonging to required units");

        // Get the dbIds of the studies belonging to the required exp units
        locationDbIds = pendingStudyByNameNoScope.values().stream().map(pio -> pio.getBrAPIObject().getLocationDbId()).collect(Collectors.toSet());
        try {
            // Get the locations belonging to required exp units
            brapiLocations = locationService.fetchLocationsByDbId(locationDbIds, program);

            // Construct the pending locations from the BrAPI locations
            pendingLocations = brapiLocations.stream().map(locationService::constructPIOFromBrapiLocation).collect(Collectors.toList());

            // Construct a hashmap to look up the pending location by location name
            pendingLocationByName = pendingLocations.stream().collect(Collectors.toMap(pio -> pio.getBrAPIObject().getName(), pio -> pio));

            // Add the map to the context for use in processing import
            context.getPendingData().setLocationByName(pendingLocationByName);
        } catch (ApiException e) {
            this.compensate(context);
        }

        return processNext(context);
    }
}
