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

package org.breedinginsight.db.migration;

import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.model.queryParams.core.LocationQueryParams;
import org.brapi.client.v2.modules.core.LocationsApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPILocation;
import org.brapi.v2.model.core.response.BrAPILocationListResponse;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.utilities.Utilities;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Slf4j
public class V1_0_13__Update_BrAPI_Locations_XRefs extends BaseJavaMigration {

    final private String DEFAULT_URL_KEY = "default-url";
    final private String BRAPI_REFERENCE_SOURCE_KEY = "brapi-reference-source";

    public void migrate(Context context) throws Exception {
        Map<String, String> placeholders = context.getConfiguration().getPlaceholders();
        String defaultUrl = placeholders.get(DEFAULT_URL_KEY);
        String referenceSource = placeholders.get(BRAPI_REFERENCE_SOURCE_KEY);

        // Get all the programs
        List<Program> programs = getAllPrograms(context, defaultUrl);
        Map<UUID, LocationsApi> locationsApiForProgram = new HashMap<>();
        for (Program program : programs) {
            BrAPIClient client = new BrAPIClient(program.getBrapiUrl(), 240000);
            locationsApiForProgram.put(program.getId(), new LocationsApi(client));
        }

        List<ProgramLocation> locations = getAllLocations(context);
        // Process all the locations in the system
        String locationReferenceSource = String.format("%s", referenceSource);
        for(ProgramLocation location : locations) {
            log.debug("Migrating BrAPI locations missing program external reference for locationId: " + location.getId());

            String programReferenceSource = String.format("%s/%s", referenceSource, ExternalReferenceSource.PROGRAMS.getName());
            var api = locationsApiForProgram.get(location.getProgramId());

            LocationQueryParams queryParams = new LocationQueryParams();
            queryParams.externalReferenceSource(locationReferenceSource);
            queryParams.externalReferenceID(location.getId().toString());
            queryParams.page(0);
            queryParams.pageSize(1000);
            ApiResponse<BrAPILocationListResponse> locationsResponse = api.locationsGet(queryParams);

            if(locationsResponse.getBody().getResult().getData().size() == 1) {
                BrAPILocation brapiLocation = locationsResponse.getBody().getResult().getData().get(0);
                Optional<BrAPIExternalReference> programRef = Utilities.getExternalReference(brapiLocation.getExternalReferences(), programReferenceSource);

                if(programRef.isEmpty()) {
                    BrAPIExternalReference programIdRef = new BrAPIExternalReference()
                            .referenceID(location.getProgramId().toString())
                            .referenceSource(String.format("%s/%s", referenceSource, ExternalReferenceSource.PROGRAMS.getName()));
                    brapiLocation.getExternalReferences().add(programIdRef);

                    api.locationsLocationDbIdPut(brapiLocation.getLocationDbId(), brapiLocation);
                }
            }
        }
        log.debug("Done updating locations");
    }

    private List<ProgramLocation> getAllLocations(Context context) throws SQLException {
        List<ProgramLocation> locations = new ArrayList<>();
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT * FROM place")) {
                while (rows.next()) {
                    ProgramLocation location = new ProgramLocation();
                    location.setId(UUID.fromString(rows.getString("id")));
                    location.setProgramId(UUID.fromString(rows.getString("program_id")));
                    locations.add(location);
                }
            }
        }
        return locations;
    }

    private List<Program> getAllPrograms(Context context, String defaultUrl) throws Exception {
        List<Program> programs = new ArrayList<>();
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT id, brapi_url, key FROM program where active = true ORDER BY id")) {
                while (rows.next()) {
                    Program program = new Program();
                    program.setId(UUID.fromString(rows.getString(1)));
                    String brapi_url = rows.getString(2);
                    if (brapi_url == null) brapi_url = defaultUrl;
                    program.setBrapiUrl(brapi_url);
                    program.setKey(rows.getString(3));
                    programs.add(program);
                }
            }
        }
        return programs;
    }
}