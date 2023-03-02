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
package org.breedinginsight.brapps.importer.services.processors;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.ProgramLocationRequest;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.base.Location;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.model.User;
import org.breedinginsight.services.ProgramLocationService;
import org.breedinginsight.services.exceptions.ValidatorException;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Prototype
@Slf4j
public class LocationProcessor implements Processor {

    private static final String NAME = "Location";

    private ProgramLocationService locationService;
    private Map<String, PendingImportObject<ProgramLocation>> locationByName = new HashMap<>();

    @Inject
    public LocationProcessor(ProgramLocationService locationService) {
        this.locationService = locationService;
    }

    public void getExistingBrapiData(List<BrAPIImport> importRows, Program program) {

        List<String> uniqueLocationNames = importRows.stream()
                .map(locationImport -> locationImport.getLocation().getLocationName())
                .distinct()
                .collect(Collectors.toList());
        List<ProgramLocation> existingLocations;

        try {
            existingLocations = locationService.getLocationsByName(uniqueLocationNames, program.getId());
            existingLocations.forEach(existingLocation -> {
                locationByName.put(existingLocation.getName(), new PendingImportObject<>(ImportObjectState.EXISTING, existingLocation));
            });
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }

    }

    @Override
    public Map<String, ImportPreviewStatistics> process(List<BrAPIImport> importRows,
                                                        Map<Integer, PendingImport> mappedBrAPIImport, Table data,
                                                        Program program, User user, boolean commit) throws ValidatorException {

        for (int i = 0; i < importRows.size(); i++) {
            BrAPIImport brapiImport = importRows.get(i);
            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(i, new PendingImport());

            Location location = brapiImport.getLocation();

            ProgramLocation brapiLocation = location.constructLocation();
            if (!locationByName.containsKey(location.getLocationName())) {
                locationByName.put(brapiLocation.getName(), new PendingImportObject<>(ImportObjectState.NEW, brapiLocation));
                mappedImportRow.setLocation(new PendingImportObject<>(ImportObjectState.NEW, brapiLocation));
            }
            mappedImportRow.setLocation(locationByName.get(location.getLocationName()));
            mappedBrAPIImport.put(i, mappedImportRow);
        }

        ImportPreviewStatistics stats = ImportPreviewStatistics.builder()
                .newObjectCount(ProcessorData.getNumNewObjects(locationByName))
                .ignoredObjectCount(ProcessorData.getNumExistingObjects(locationByName))
                .build();

        return Map.of(NAME, stats);
    }

    @Override
    public void validateDependencies(Map<Integer, PendingImport> mappedBrAPIImport) throws ValidatorException {
        // no dependencies
    }

    @Override
    public void postBrapiData(Map<Integer, PendingImport> mappedBrAPIImport, Program program, ImportUpload upload) throws ValidatorException {
        List<ProgramLocationRequest> locations = ProcessorData.getNewObjects(this.locationByName)
                                                                 .stream()
                                                                 .map(location -> ProgramLocationRequest.builder()
                                                                                                        .name(location.getName())
                                                                                                        .build())
                                                                 .collect(Collectors.toList());

        AuthenticatedUser actingUser = new AuthenticatedUser(upload.getUpdatedByUser().getName(), new ArrayList<>(), upload.getUpdatedByUser().getId(), new ArrayList<>());
        List<ProgramLocation> createdLocations = new ArrayList<>();
        try {
            createdLocations.addAll(locationService.create(actingUser, program.getId(), locations));
        } catch (Exception e) {
            log.error("Error saving location import", e);
            throw new InternalServerException(e.getMessage(), e);
        }

        // Update our records
        createdLocations.forEach(location -> {
            PendingImportObject<ProgramLocation> preview = locationByName.get(location.getName());
            preview.setBrAPIObject(location);
        });
    }

    @Override
    public String getName() {
        return NAME;
    }
}
