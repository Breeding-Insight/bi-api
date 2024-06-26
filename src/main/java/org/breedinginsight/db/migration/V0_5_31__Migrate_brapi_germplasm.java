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
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.model.queryParams.germplasm.GermplasmQueryParams;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.response.BrAPIGermplasmListResponse;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.dao.db.tables.pojos.BreedingMethodEntity;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.utilities.Utilities;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

@Slf4j
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

        // Process the germplasm for each program sequentially
        for (Program program : programs) {
            log.debug("Migrating germplasm for programId: " + program.getId());
            BrAPIClient client = new BrAPIClient(program.getBrapiUrl(), 240000);
            GermplasmApi api = new GermplasmApi(client);

            GermplasmQueryParams queryParams = new GermplasmQueryParams();
            String programReferenceSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS);
            queryParams.externalReferenceSource(programReferenceSource);
            queryParams.externalReferenceID(program.getId().toString());
            queryParams.page(0);
            queryParams.pageSize(1000);
            ApiResponse<BrAPIGermplasmListResponse> germplasmResponse = api.germplasmGet(queryParams);

            boolean done = germplasmResponse.getBody() == null || germplasmResponse.getBody().getMetadata().getPagination().getTotalCount() == 0 || germplasmResponse.getBody().getResult() == null;
            while(!done) {
                log.debug(String.format("processing page %d of %d of germplasm for programId: %s", germplasmResponse.getBody().getMetadata().getPagination().getCurrentPage()+1, germplasmResponse.getBody().getMetadata().getPagination().getTotalPages(), program.getId()));
                List<BrAPIGermplasm> updateGermplasm = new ArrayList<>();
                // Filter out germplasm that have already been updated
                for (BrAPIGermplasm germplasm : germplasmResponse.getBody().getResult().getData()) {
                    // Check the germplasm are for this program
                    Boolean hasProgramRef = false;
                    for (BrAPIExternalReference reference : germplasm.getExternalReferences()) {
                        if (reference.getReferenceSource().equals(programReferenceSource) &&
                                reference.getReferenceID().equals(program.getId().toString())) {
                            hasProgramRef = true;
                        }
                    }

                    if (hasProgramRef && !isUpdated(germplasm, program, referenceSource)) {
                        updateGermplasm.add(germplasm);
                    }
                }

                // If a program has germplasm, and it doesn't have a program key, throw an error
                if (program.getKey() == null && updateGermplasm.size() > 0) {
                    throw new Exception("Unable to process germplasm for program with no 'key'");
                }

                // Checks complete, update the germplasm
                log.debug(String.format("Updating %d germplasm for programId: %s", updateGermplasm.size(), program.getId()));
                for (int i = 0; i < updateGermplasm.size(); i++) {
                    BrAPIGermplasm germplasm = updateGermplasm.get(i);
                    log.debug(String.format("Program Key: %s. Germplasm %s out of %s", program.getKey(), i + 1, updateGermplasm.size()));

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
                    germplasm.getExternalReferences()
                             .add(uuidReference);
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

                //fetch the next page of germplasm for this program
                if(germplasmResponse.getBody().getMetadata().getPagination().getCurrentPage()+1 == germplasmResponse.getBody().getMetadata().getPagination().getTotalPages()) {
                    done = true;
                } else {
                    queryParams.page(queryParams.page() + 1);
                    log.debug(String.format("Fetching the next page of germplasm for programId: %s", program.getId()));
                    germplasmResponse = api.germplasmGet(queryParams);
                }
            }
            log.debug(String.format("Done migrating germplasm for programId: %s", program.getId()));
        }
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