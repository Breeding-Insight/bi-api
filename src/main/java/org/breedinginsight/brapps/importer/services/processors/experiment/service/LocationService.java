/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
}
