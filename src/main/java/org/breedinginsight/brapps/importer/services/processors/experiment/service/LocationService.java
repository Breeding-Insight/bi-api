package org.breedinginsight.brapps.importer.services.processors.experiment.service;

import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.services.ProgramLocationService;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.COMMA_DELIMITER;

@Singleton
@Slf4j
public class LocationService {
    private final ProgramLocationService programLocationService;
    public LocationService(ProgramLocationService programLocationService) {
        this.programLocationService = programLocationService;
    }

    /**
     * Fetches a list of ProgramLocation objects based on the provided locationDbIds and program ID.
     *
     * @param locationDbIds A set of locationDbIds used to query locations.
     * @param program The Program object for which locations are being fetched.
     * @return A list of ProgramLocation objects matching the given locationDbIds for the program.
     * @throws ApiException if there is an issue with fetching the locations or if any location(s) are not found.
     */
    public List<ProgramLocation> fetchLocationsByDbId(Set<String> locationDbIds, Program program) throws ApiException {
        List<ProgramLocation> programLocations = null; // Initializing the ProgramLocations list

        // Retrieving locations based on the locationDbId and the program's ID
        programLocations = programLocationService.getLocationsByDbId(locationDbIds, program.getId());

        // If no locations are found, throw an IllegalStateException with an error message
        if (locationDbIds.size() != programLocations.size()) {
            Set<String> missingIds = new HashSet<>(locationDbIds);
            missingIds.removeAll(programLocations.stream().map(ProgramLocation::getLocationDbId).collect(Collectors.toSet()));
            throw new IllegalStateException("Location not found for location dbid(s): " + String.join(COMMA_DELIMITER, missingIds));
        }

        // Return the fetched ProgramLocations
        return programLocations;
    }

    /**
     * Constructs a PendingImportObject of type ProgramLocation from a given BrAPI ProgramLocation.
     * This method creates a new PendingImportObject with the state set to EXISTING and the BrAPI ProgramLocation as the source object.
     *
     * @param brapiLocation The BrAPI ProgramLocation from which the PendingImportObject should be constructed
     * @return PendingImportObject<ProgramLocation> The PendingImportObject created from the BrAPI ProgramLocation
     */
    public PendingImportObject<ProgramLocation> constructPIOFromBrapiLocation(ProgramLocation brapiLocation) {
        return new PendingImportObject<>(ImportObjectState.EXISTING, brapiLocation, brapiLocation.getId());
    }

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
                existingLocations.addAll(programLocationService.getLocationsByDbId(locationDbIds, program.getId()));
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
//    public Map<String, PendingImportObject<ProgramLocation>> initializeUniqueLocationNames(Program program, List<ExperimentObservation> experimentImportRows) {
//        Map<String, PendingImportObject<ProgramLocation>> locationByName = new HashMap<>();
//
//        List<ProgramLocation> existingLocations = new ArrayList<>();
//        if(studyByNameNoScope.size() > 0) {
//            Set<String> locationDbIds = studyByNameNoScope.values()
//                    .stream()
//                    .map(study -> study.getBrAPIObject()
//                            .getLocationDbId())
//                    .collect(Collectors.toSet());
//            try {
//                existingLocations.addAll(locationService.getLocationsByDbId(locationDbIds, program.getId()));
//            } catch (ApiException e) {
//                log.error("Error fetching locations: " + Utilities.generateApiExceptionLogMessage(e), e);
//                throw new InternalServerException(e.toString(), e);
//            }
//        }
//
//        List<String> uniqueLocationNames = experimentImportRows.stream()
//                .filter(experimentObservation -> StringUtils.isBlank(experimentObservation.getObsUnitID()))
//                .map(ExperimentObservation::getEnvLocation)
//                .distinct()
//                .filter(Objects::nonNull)
//                .collect(Collectors.toList());
//
//        try {
//            existingLocations.addAll(locationService.getLocationsByName(uniqueLocationNames, program.getId()));
//        } catch (ApiException e) {
//            log.error("Error fetching locations: " + Utilities.generateApiExceptionLogMessage(e), e);
//            throw new InternalServerException(e.toString(), e);
//        }
//
//        existingLocations.forEach(existingLocation -> locationByName.put(existingLocation.getName(), new PendingImportObject<>(ImportObjectState.EXISTING, existingLocation, existingLocation.getId())));
//        return locationByName;
//    }

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

    // TODO: used by expunit workflow
//    private void fetchOrCreateLocationPIO(ImportContext importContext, ExpUnitContext expUnitContext) {
//        PendingImportObject<ProgramLocation> pio;
//        String envLocationName = pendingObsUnitByOUId.get(importRow.getObsUnitID()).getBrAPIObject().getLocationName();
//        if (!locationByName.containsKey((importRow.getEnvLocation()))) {
//            ProgramLocation newLocation = new ProgramLocation();
//            newLocation.setName(envLocationName);
//            pio = new PendingImportObject<>(ImportObjectState.NEW, newLocation, UUID.randomUUID());
//            this.locationByName.put(envLocationName, pio);
//        }
//    }

    // TODO: used by create workflow
//    private void fetchOrCreateLocationPIO(ImportContext importContext) {
//        PendingImportObject<ProgramLocation> pio;
//        String envLocationName = importRow.getEnvLocation();
//        if (!locationByName.containsKey((importRow.getEnvLocation()))) {
//            ProgramLocation newLocation = new ProgramLocation();
//            newLocation.setName(envLocationName);
//            pio = new PendingImportObject<>(ImportObjectState.NEW, newLocation, UUID.randomUUID());
//            this.locationByName.put(envLocationName, pio);
//        }
//    }

    // TODO: used by both workflows
//    public List<ProgramLocation> commitNewPendingLocationsToBrAPIStore(ImportContext importContext, PendingData pendingData) {
//        AuthenticatedUser actingUser = new AuthenticatedUser(upload.getUpdatedByUser().getName(), new ArrayList<>(), upload.getUpdatedByUser().getId(), new ArrayList<>());
//
//        List<ProgramLocationRequest> newLocations = ProcessorData.getNewObjects(this.locationByName)
//                .stream()
//                .map(location -> ProgramLocationRequest.builder()
//                        .name(location.getName())
//                        .build())
//                .collect(Collectors.toList());
//
//        List<ProgramLocation> createdLocations = new ArrayList<>(locationService.create(actingUser, program.getId(), newLocations));
//        // set the DbId to the for each newly created location
//        for (ProgramLocation createdLocation : createdLocations) {
//            String createdLocationName = createdLocation.getName();
//            this.locationByName.get(createdLocationName)
//                    .getBrAPIObject()
//                    .setLocationDbId(createdLocation.getLocationDbId());
//        }
//        return createdLocations;
//    }

    // TODO: used by both workflows
//    public List<ProgramLocation> commitUpdatedPendingLocationsToBrAPIStore(ImportContext importContext, PendingData pendingData) {
//        List<ProgramLocation> updatedLocations = new ArrayList<>();
//
//        return updatedLocations;
//    }
}
