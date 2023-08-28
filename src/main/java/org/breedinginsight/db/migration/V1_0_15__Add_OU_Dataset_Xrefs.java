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

import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.model.queryParams.core.TrialQueryParams;
import org.brapi.client.v2.model.queryParams.phenotype.ObservationUnitQueryParams;
import org.brapi.client.v2.modules.core.TrialsApi;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPITrialListResponse;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.response.BrAPIObservationUnitListResponse;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.daos.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.daos.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.daos.impl.BrAPITrialDAOImpl;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.Utilities;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class V1_0_15__Add_OU_Dataset_Xrefs extends BaseJavaMigration {


    public void migrate(Context context) throws Exception {
        Map<String, String> placeholders = context.getConfiguration().getPlaceholders();
        String DEFAULT_URL_KEY = "default-url";
        String defaultUrl = placeholders.get(DEFAULT_URL_KEY);
        String BRAPI_REFERENCE_SOURCE_KEY = "brapi-reference-source";
        String referenceSource = placeholders.get(BRAPI_REFERENCE_SOURCE_KEY);
        String programReferenceSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS);
        String trialReferenceSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.TRIALS);
        String observationUnitReferenceSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.OBSERVATION_UNITS);
        String datasetReferenceSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.DATASET);

        // Get all the programs
        List<Program> programs = Utilities.getAllProgramsFlyway(context, defaultUrl);

        // For each program, update any observation units created via Deltabreed
        for (Program program : programs) {
            BrAPIClient client = new BrAPIClient(program.getBrapiUrl(), 240000);
            log.debug("Migrating observation units for programId: " + program.getId());

            // Get the Deltabreed-generated experiments for the program
            TrialsApi trialsApi = new TrialsApi(client);
            TrialQueryParams trialQueryParams = new TrialQueryParams();
            trialQueryParams.externalReferenceSource(programReferenceSource);
            trialQueryParams.externalReferenceID(program.getId().toString());
            trialQueryParams.page(0);
            trialQueryParams.pageSize(1000);
            ApiResponse<BrAPITrialListResponse> trialsResponse = trialsApi.trialsGet(trialQueryParams);

            boolean trialsDone = trialsResponse.getBody() == null || trialsResponse.getBody().getMetadata().getPagination().getTotalCount() == 0 || trialsResponse.getBody().getResult() == null;
            while(!trialsDone) {
                log.debug(String.format("processing page %d of %d of experiments for programId: %s",
                        trialsResponse.getBody().getMetadata().getPagination().getCurrentPage()+1,
                        trialsResponse.getBody().getMetadata().getPagination().getTotalPages(),
                        program.getId()));
                List<BrAPITrial> experiments = trialsResponse.getBody().getResult().getData().stream().filter(trial -> {
                    List<BrAPIExternalReference> xrefs = trial.getExternalReferences();
                    Optional<BrAPIExternalReference> programRef = Utilities.getExternalReference(xrefs,programReferenceSource);
                    return trial.getAdditionalInfo().getAsJsonObject().has(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID) &&
                            programRef.isPresent() &&
                            program.getId().equals(UUID.fromString(programRef.get().getReferenceID()));
                }).collect(Collectors.toList());

                Map<String, String> ExpIdByDbId = new HashMap<>();
                experiments.forEach(exp -> {
                    Optional<BrAPIExternalReference> expRef = Utilities.getExternalReference(exp.getExternalReferences(), trialReferenceSource);
                    expRef.ifPresent(brAPIExternalReference -> ExpIdByDbId.put(exp.getTrialDbId(), brAPIExternalReference.getReferenceID()));
                });

                for (BrAPITrial exp : experiments) {

                    // Fetch all observation units for an experiment
                    ObservationUnitsApi ousApi = new ObservationUnitsApi(client);
                    ObservationUnitQueryParams ouQueryParams = new ObservationUnitQueryParams();
                    ouQueryParams.externalReferenceSource(trialReferenceSource);
                    ouQueryParams.externalReferenceID(ExpIdByDbId.get(exp.getTrialDbId()));
                    ouQueryParams.page(0);
                    ouQueryParams.pageSize(1000);
                    ApiResponse<BrAPIObservationUnitListResponse> ousResponse = ousApi.observationunitsGet(ouQueryParams);

                    boolean ousDone = ousResponse.getBody() == null || ousResponse.getBody().getMetadata().getPagination().getTotalCount() == 0 || ousResponse.getBody().getResult() == null;
                    while(!ousDone) {
                        log.debug(String.format("processing page %d of %d of observation units for experiment: %s",
                                ousResponse.getBody().getMetadata().getPagination().getCurrentPage()+1,
                                ousResponse.getBody().getMetadata().getPagination().getTotalPages(),
                                exp.getTrialName()));

                        ousResponse.getBody().getResult().getData()
                                .stream().filter(ou -> {

                                    // Find the observation units that need a dataset reference
                                    List<BrAPIExternalReference> xrefs = ou.getExternalReferences();
                                    Optional<BrAPIExternalReference> expRef = Utilities.getExternalReference(xrefs, trialReferenceSource);
                                    Optional<BrAPIExternalReference> datasetRef = Utilities.getExternalReference(xrefs, datasetReferenceSource);
                                    return datasetRef.isEmpty() &&
                                            expRef.isPresent() &&
                                            ExpIdByDbId.get(exp.getTrialDbId()).equals(expRef.get().getReferenceID());
                                }).forEach(ou -> {

                                    // Assign the experiment Observation Dataset id to the observation units
                                    BrAPIExternalReference datasetRef = new BrAPIExternalReference()
                                            .referenceSource(datasetReferenceSource)
                                            .referenceID(exp.getAdditionalInfo().getAsJsonObject().get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID).getAsString());
                                    ou.getExternalReferences().add(datasetRef);
                                    try {

                                        // Send the updated observation unit back to the brapi server
                                        log.debug(String.format("Sending observation unit (id: %s) with observation dataset (id: %s) to %s",
                                                Utilities.getExternalReference(ou.getExternalReferences(), observationUnitReferenceSource),
                                                datasetRef.getReferenceID(),
                                                defaultUrl));
                                        BrAPIObservationUnit updatedOu = ousApi.observationunitsObservationUnitDbIdPut(ou.getObservationUnitDbId(), ou).getBody().getResult();

                                        // Verify that the observation unit was updated at the server
                                        boolean isUpdated = updatedOu.getExternalReferences().stream().anyMatch(xref -> {
                                            return xref.getReferenceSource().equals(datasetReferenceSource) &&
                                                    xref.getReferenceID().equals(exp.getAdditionalInfo().getAsJsonObject().get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID).getAsString());
                                        });
                                        log.debug("Updating observation unit successful: " + String.valueOf(isUpdated));
                                        if (!isUpdated) {
                                            throw new Exception("Observation unit returned from brapi server was not updated. Check your brapi server.");
                                        }
                                    } catch(Exception e) {
                                        log.error(e.getMessage(), e);
                                        throw new InternalServerException(e.toString(), e);
                                    }
                                });

                        // Fetch the next page of observation units for this experiment
                        if(ousResponse.getBody().getMetadata().getPagination().getCurrentPage() + 1 == ousResponse.getBody().getMetadata().getPagination().getTotalPages()) {
                            ousDone = true;
                        } else {
                            ouQueryParams.page(ouQueryParams.page() + 1);
                            ousResponse = ousApi.observationunitsGet(ouQueryParams);
                        }
                    }
                }

                // Fetch the next page of experiments for this program
                if(trialsResponse.getBody().getMetadata().getPagination().getCurrentPage() + 1 == trialsResponse.getBody().getMetadata().getPagination().getTotalPages()) {
                    trialsDone = true;
                } else {
                    trialQueryParams.page(trialQueryParams.page() + 1);
                    trialsResponse = trialsApi.trialsGet(trialQueryParams);
                }
            }
        }
    }
}
