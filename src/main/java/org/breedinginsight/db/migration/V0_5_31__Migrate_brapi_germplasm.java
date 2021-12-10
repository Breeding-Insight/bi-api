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
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.response.BrAPIGermplasmListResponse;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.dao.db.tables.pojos.BreedingMethodEntity;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

/**
 * Example of a Java-based migration.
 */
public class V0_5_31__Migrate_brapi_germplasm extends BaseJavaMigration {

    @Inject
    private DSLContext dsl;
    @Inject
    private UserDAO userDAO;

    final private String DEFAULT_URL_KEY = "default-url";
    final private String BRAPI_REFERENCE_SOURCE_KEY = "brapi-reference-source";

    public void migrate(Context context) throws Exception {
        Map<String, String> placeholders = context.getConfiguration().getPlaceholders();
        String defaultUrl = placeholders.get(DEFAULT_URL_KEY);
        String referenceSource = placeholders.get(BRAPI_REFERENCE_SOURCE_KEY);

        // Get the system user
        User systemUser = getSystemUser(context);
        // Get all the programs
        List<Program> programs = getAllPrograms(context, defaultUrl);

        // Get all of the germplasm with a program id attached
        Map<String, List<BrAPIGermplasm>> programGermplasm = new HashMap<>();
        for (Program program : programs) {
            System.out.println(String.format("id=%s and brapi_url=%s", program.getId(), program.getBrapiUrl()));
            BrAPIClient client = new BrAPIClient(program.getBrapiUrl());
            GermplasmApi api = new GermplasmApi(client);

            GermplasmQueryParams queryParams = new GermplasmQueryParams();
            String programReferenceSource = String.format("%s/programs", referenceSource);
            queryParams.externalReferenceSource(programReferenceSource);
            queryParams.externalReferenceID(program.getId().toString());
            ApiResponse<BrAPIGermplasmListResponse> germplasmResponse = api.germplasmGet(queryParams);

            List<BrAPIGermplasm> updateGermplasm = new ArrayList<>();
            if (germplasmResponse.getBody() != null && germplasmResponse.getBody().getResult() != null) {
                // Filter out germplasm that have already been updated
                for (BrAPIGermplasm germplasm: germplasmResponse.getBody().getResult().getData()) {
                    // Check the germplasm are for this program
                    Boolean hasProgramRef = false;
                    for (BrAPIExternalReference reference: germplasm.getExternalReferences()) {
                        if (reference.getReferenceSource().equals(programReferenceSource) &&
                                reference.getReferenceID().equals(program.getId().toString())
                        ) {
                            hasProgramRef = true;
                        }
                    }

                    if (hasProgramRef && !isUpdated(germplasm, program, referenceSource)) {
                        updateGermplasm.add(germplasm);
                    }
                }
            }

            // If a program has germplasm, and it doesn't have a program key, throw an error
            if (program.getKey() == null && updateGermplasm.size() > 0) {
                throw new Exception("Unable to process germplasm for program with no 'key'");
            }

            programGermplasm.put(program.getId().toString(), updateGermplasm);
        }

        // Checks complete, update the germplasm
        for (Program program: programs) {

            // Update germplasm
            List<BrAPIGermplasm> allGermplasm = programGermplasm.get(program.getId().toString());

            BrAPIClient client = new BrAPIClient(program.getBrapiUrl());
            GermplasmApi api = new GermplasmApi(client);
            for (int i = 0; i < allGermplasm.size(); i++) {
                BrAPIGermplasm germplasm = allGermplasm.get(i);

                // Seed Source Description
                if (germplasm.getSeedSourceDescription() == null || germplasm.getSeedSourceDescription().isBlank()) {
                    germplasm.setSeedSource("Unknown");
                }
                // Set createdBy user
                Map<String, String> additionalInfo = new HashMap<>();
                additionalInfo.put("userId", systemUser.getId().toString());
                additionalInfo.put("userName", systemUser.getName());
                germplasm.putAdditionalInfoItem("createdBy", additionalInfo);
                // Map breeding method
                BreedingMethodEntity breedingMethod = getMappedBreedingMethod(context, germplasm.getBreedingMethodDbId());
                germplasm.putAdditionalInfoItem("breedingMethodId", breedingMethod.getId().toString());
                germplasm.putAdditionalInfoItem("breedingMethod", breedingMethod.getName().strip());
                // Add germplasm UUID
                BrAPIExternalReference uuidReference = new BrAPIExternalReference();
                uuidReference.setReferenceSource(referenceSource);
                uuidReference.setReferenceID(UUID.randomUUID().toString());
                germplasm.getExternalReferences().add(uuidReference);
                // Set display name to the old germplasm name
                germplasm.setDefaultDisplayName(germplasm.getGermplasmName());
                Integer accessionNumber = getNextSequenceVal(context, program);
                germplasm.setAccessionNumber(accessionNumber.toString());
                // Germplasm name = <Name> [<program key>-<accessionNumber>]
                germplasm.setGermplasmName(String.format("%s [%s-%s]", germplasm.getGermplasmName(), program.getKey(), germplasm.getAccessionNumber()));
                // Send germplasm back to server
                BrAPIGermplasm updatedGermplasm = api.germplasmGermplasmDbIdPut(germplasm.getGermplasmDbId(), germplasm).getBody().getResult();
                // Check that the germplasm was in fact updated
                if (!isUpdated(updatedGermplasm, program, referenceSource)) {
                    throw new Exception("Germplasm returned from brapi server was not updated. Check your brapi server.");
                }
            }
        }

        throw new Exception("NOOO!");
    }

    private Integer getNextSequenceVal(Context context, Program program) throws Exception {
        Integer nextVal;
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery(String.format("select nextval(germplasm_sequence) from program where id = '%s'", program.getId().toString()))) {
                while (rows.next()) {
                    nextVal = rows.getInt(1);
                    return nextVal;
                }
            }
        }
        throw new Exception("Next germplasm sequence was not generated properly");
    }

    private User getSystemUser(Context context) throws Exception{
        User systemUser = new User();
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT id, name FROM bi_user where name = 'system'")) {
                while (rows.next()) {
                    systemUser.setId(UUID.fromString(rows.getString(1)));
                    systemUser.setName(rows.getString(2));
                }
            }
        }
        return systemUser;
    }

    private List<Program> getAllPrograms(Context context, String defaultUrl) throws Exception {
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
        return programs;
    }

    private Boolean isUpdated(BrAPIGermplasm germplasm, Program program, String referenceSource) {
        // Check accession number
        Boolean hasAccessionNumber = germplasm.getAccessionNumber() != null && !germplasm.getAccessionNumber().isBlank();
        // Check name for formatting
        String expectedName = String.format("%s [%s-%s]", germplasm.getDefaultDisplayName(), program.getKey(), germplasm.getAccessionNumber());
        Boolean hasNameFormatting = germplasm.getGermplasmName().equals(expectedName);
        // Check external references for UUID
        Boolean hasUUID = germplasm.getExternalReferences().stream()
                .filter(reference -> reference.getReferenceSource().equals(referenceSource))
                .count() >= 1;
        return hasAccessionNumber && hasNameFormatting && hasUUID;
    }

    private BreedingMethodEntity getMappedBreedingMethod(Context context, String breedingMethod) throws Exception {
        String mappedBreedingMethod = "UNK";

        Map<String, String> breedingMap = new HashMap<>();
        breedingMap.put("biparental", "BPC");
        breedingMap.put("self", "SLF");
        breedingMap.put("open", "OFT");
        breedingMap.put("backcross", "BCR");
        breedingMap.put("sib", "BPC");
        breedingMap.put("polycross", "POC");
        breedingMap.put("reselected", "SBK");
        breedingMap.put("bulk", "RBK");
        breedingMap.put("bulk selfed", "SLF");
        breedingMap.put("bulk and open pollinated", "OFT");
        breedingMap.put("double haploid", "DHL");
        breedingMap.put("reciprocal", "BPC");
        breedingMap.put("multicross", "CCX");

        if (breedingMethod != null && breedingMap.containsKey(breedingMethod.toLowerCase())) {
            mappedBreedingMethod = breedingMap.get(breedingMethod.toLowerCase());
        }

        // Get the breeding method
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery(String.format("SELECT id, name FROM breeding_method where abbreviation = '%s'", mappedBreedingMethod))) {
                while (rows.next()) {
                    BreedingMethodEntity foundBreedingMethod = new BreedingMethodEntity();
                    foundBreedingMethod.setId(UUID.fromString(rows.getString(1)));
                    foundBreedingMethod.setName(rows.getString(2));
                    return foundBreedingMethod;
                }
            }
        }
        throw new Exception("Unable to find breeding method");
    }
}