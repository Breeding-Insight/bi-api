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

import io.micronaut.context.annotation.Property;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.model.queryParams.germplasm.GermplasmQueryParams;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.response.BrAPIGermplasmListResponse;
import org.breedinginsight.model.Program;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

/**
 * Example of a Java-based migration.
 */
public class V0_5_28__Migrate_brapi_germplasm extends BaseJavaMigration {

    final private String DEFAULT_URL_KEY = "default-url";
    final private String BRAPI_REFERENCE_SOURCE_KEY = "brapi-reference-source";

    public void migrate(Context context) throws Exception {
        // Get all the programs
        Map<String, String> placeholders = context.getConfiguration().getPlaceholders();
        String defaultUrl = placeholders.get(DEFAULT_URL_KEY);
        String referenceSource = placeholders.get(BRAPI_REFERENCE_SOURCE_KEY);

        // Get all the programs
        List<Program> programs = new ArrayList<>();
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT id, brapi_url, key FROM program ORDER BY id")) {
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

        // Get all of the germplasm with a program id attached
        Map<String, List<BrAPIGermplasm>> programGermplasm = new HashMap<>();
        for (Program program : programs) {
            System.out.println(String.format("id=%s and brapi_url=%s", program.getId(), program.getBrapiUrl()));
            BrAPIClient client = new BrAPIClient(program.getBrapiUrl());
            GermplasmApi api = new GermplasmApi(client);

            GermplasmQueryParams queryParams = new GermplasmQueryParams();
            String programReferenceSource = String.format("%s/programs", referenceSource);
            queryParams.externalReferenceSource(programReferenceSource);
            ApiResponse<BrAPIGermplasmListResponse> germplasmResponse = api.germplasmGet(queryParams);

            // TODO: Check if the germplasm has already been updated. How to do? This might be run twice in the case of an error and brapi doesn't do transactions
            List<BrAPIGermplasm> allGermplasm = new ArrayList<>();
            if (germplasmResponse.getBody() != null) {
                if (germplasmResponse.getBody().getResult() != null) {
                    allGermplasm.addAll(germplasmResponse.getBody().getResult().getData());
                }
            }

            // If a program has germplasm, and it doesn't have a program key, throw an error
            if (program.getKey() == null && allGermplasm.size() > 0) {
                throw new Exception("Unable to process germplasm for program with no 'key'");
            }

            programGermplasm.put(program.getId().toString(), allGermplasm);
        }

        // Checks complete, update the germplasm
        for (Program program: programs) {

            // Update germplasm
            List<BrAPIGermplasm> allGermplasm = programGermplasm.get(program.getId().toString());

            List<BrAPIGermplasm> updatedGermplasm = new ArrayList<>();
            // TODO: Get the next n sequences from the germplasm sequence.
            for (int i = 0; i < allGermplasm.size(); i++) {
                BrAPIGermplasm germplasm = allGermplasm.get(i);
                // Set display name to the old germplasm name
                germplasm.setDefaultDisplayName(germplasm.getGermplasmName());
                // TODO: Fill accession number and number in once we have the germplasm sequence
                germplasm.setAccessionNumber("");
                // Germplasm name = <Name> [<program key>-<accessionNumber>]
                germplasm.setGermplasmName("");
                updatedGermplasm.add(germplasm);
            }

            // TODO: Send germplasm back to server
            BrAPIClient client = new BrAPIClient(program.getBrapiUrl());
            GermplasmApi api = new GermplasmApi(client);
            for (BrAPIGermplasm germplasm: updatedGermplasm) {
                api.germplasmGermplasmDbIdPut(germplasm.getGermplasmDbId(), germplasm);
            }
        }

        throw new Exception("NOOO!");
    }
}