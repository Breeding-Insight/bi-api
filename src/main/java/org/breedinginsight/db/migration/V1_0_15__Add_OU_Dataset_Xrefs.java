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
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.daos.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.daos.BrAPITrialDAO;
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
    private final BrAPITrialDAO trialDAO;
    private final BrAPIObservationUnitDAO ouDAO;

    @Inject
    V1_0_15__Add_OU_Dataset_Xrefs(BrAPITrialDAO trialDAO, BrAPIObservationUnitDAO ouDAO) {
        this.trialDAO = trialDAO;
        this.ouDAO = ouDAO;
    }

    public void migrate(Context context) throws Exception {
        Map<String, String> placeholders = context.getConfiguration().getPlaceholders();
        String DEFAULT_URL_KEY = "default-url";
        String defaultUrl = placeholders.get(DEFAULT_URL_KEY);
        String BRAPI_REFERENCE_SOURCE_KEY = "brapi-reference-source";
        String referenceSource = placeholders.get(BRAPI_REFERENCE_SOURCE_KEY);
        String programReferenceSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS);
        String trialReferenceSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.TRIALS);
        String datasetReferenceSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.DATASET);


        // Get all the programs
        List<Program> programs = Utilities.getAllProgramsFlyway(context, defaultUrl);

        // For each program, update any observation units created via Deltabreed
        for (Program program : programs) {

            // Get the Deltabreed-generated experiments for the program
            List<BrAPITrial> experiments = trialDAO.getTrials(program.getId()).stream().filter(trial -> {
                List<BrAPIExternalReference> xrefs = trial.getExternalReferences();
                Optional<BrAPIExternalReference> programRef = Utilities.getExternalReference(xrefs,programReferenceSource);
                return trial.getAdditionalInfo().getAsJsonObject().has(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID) &&
                        programRef.isPresent() &&
                        program.getId().equals(UUID.fromString(programRef.get().getReferenceID()));
            }).collect(Collectors.toList());

            Map<String, String> ExpIdByDbId = new HashMap<>();
            experiments.forEach(exp -> {
                Optional<BrAPIExternalReference> expRef = Utilities.getExternalReference(exp.getExternalReferences(), trialReferenceSource);
                expRef.ifPresent(brAPIExternalReference -> ExpIdByDbId.put(brAPIExternalReference.getReferenceID(), exp.getTrialDbId()));
            });

            for (BrAPITrial exp : experiments) {

                ouDAO.getObservationUnitsForTrialDbId(program.getId(), exp.getTrialDbId())
                        .stream().filter(ou -> {

                            // For each experiment, fetch the observation units that need a dataset reference
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
                            ouDAO.updateBrAPIObservationUnit(ou.getObservationUnitDbId(), ou, program.getId());
                        });
            }
        }
    }
}
