package org.breedinginsight.brapps.importer.services.processors.experiment.services;

import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class LocationService {

    // used by expunit workflow
    public Map<String, PendingImportObject<ProgramLocation>> initializeLocationByName(
            Program program,
            Map<String, PendingImportObject<BrAPIStudy>> studyByName) {
        Map<String, PendingImportObject<ProgramLocation>> locationByName = new HashMap<>();

        List<ProgramLocation> existingLocations = new ArrayList<>();
        if(studyByName.size() > 0) {
            Set<String> locationDbIds = studyByName.values()
                    .stream()
                    .map(study -> study.getBrAPIObject()
                            .getLocationDbId())
                    .collect(Collectors.toSet());
            try {
                existingLocations.addAll(locationService.getLocationsByDbId(locationDbIds, program.getId()));
            } catch (ApiException e) {
                log.error("Error fetching locations: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            }
        }
        existingLocations.forEach(existingLocation -> locationByName.put(
                        existingLocation.getName(),
                        new PendingImportObject<>(ImportObjectState.EXISTING, existingLocation, existingLocation.getId())
                )
        );
        return locationByName;
    }

    // TODO: used by create workflow
    public Map<String, PendingImportObject<ProgramLocation>> initializeUniqueLocationNames(Program program, List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<ProgramLocation>> locationByName = new HashMap<>();

        List<ProgramLocation> existingLocations = new ArrayList<>();
        if(studyByNameNoScope.size() > 0) {
            Set<String> locationDbIds = studyByNameNoScope.values()
                    .stream()
                    .map(study -> study.getBrAPIObject()
                            .getLocationDbId())
                    .collect(Collectors.toSet());
            try {
                existingLocations.addAll(locationService.getLocationsByDbId(locationDbIds, program.getId()));
            } catch (ApiException e) {
                log.error("Error fetching locations: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            }
        }

        List<String> uniqueLocationNames = experimentImportRows.stream()
                .filter(experimentObservation -> StringUtils.isBlank(experimentObservation.getObsUnitID()))
                .map(ExperimentObservation::getEnvLocation)
                .distinct()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        try {
            existingLocations.addAll(locationService.getLocationsByName(uniqueLocationNames, program.getId()));
        } catch (ApiException e) {
            log.error("Error fetching locations: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        }

        existingLocations.forEach(existingLocation -> locationByName.put(existingLocation.getName(), new PendingImportObject<>(ImportObjectState.EXISTING, existingLocation, existingLocation.getId())));
        return locationByName;
    }

    // TODO: used by expunit workflow
    public Map<String, PendingImportObject<ProgramLocation>> mapPendingLocationByOUId(
            String unitId,
            BrAPIObservationUnit unit,
            Map<String, PendingImportObject<BrAPIStudy>> studyByOUId,
            Map<String, PendingImportObject<ProgramLocation>> locationByName,
            Map<String, PendingImportObject<ProgramLocation>> locationByOUId
    ) {
        if (unit.getLocationName() != null) {
            locationByOUId.put(unitId, locationByName.get(unit.getLocationName()));
        } else if (studyByOUId.get(unitId) != null && studyByOUId.get(unitId).getBrAPIObject().getLocationName() != null) {
            locationByOUId.put(
                    unitId,
                    locationByName.get(studyByOUId.get(unitId).getBrAPIObject().getLocationName())
            );
        } else {
            throw new IllegalStateException("Observation unit missing location: " + unitId);
        }

        return locationByOUId;
    }
}
