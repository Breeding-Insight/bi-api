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
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapps.importer.daos.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.base.Trial;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.ValidatorException;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Prototype
public class TrialProcessor implements Processor {

    private static final String NAME = "Trial";

    private BrAPITrialDAO brapiTrialDAO;
    private Map<String, PendingImportObject<BrAPITrial>> trialByName = new HashMap<>();

    @Inject
    public TrialProcessor(BrAPITrialDAO brapiTrialDAO) {
        this.brapiTrialDAO = brapiTrialDAO;
    }

    private void getExistingBrapiObjects(List<BrAPIImport> importRows, Program program) {

        List<String> uniqueTrialNames = importRows.stream()
                .map(trialImport -> trialImport.getTrial().getTrialName())
                .distinct()
                .collect(Collectors.toList());
        List<BrAPITrial> existingTrials;

        try {
            existingTrials = brapiTrialDAO.getTrialByName(uniqueTrialNames, program.getId());
            existingTrials.forEach(existingTrial -> {
                trialByName.put(existingTrial.getTrialName(), new PendingImportObject<>(ImportObjectState.EXISTING, existingTrial));
            });
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }

    }

    @Override
    public Map<String, ImportPreviewStatistics> process(List<BrAPIImport> importRows, Map<Integer, PendingImport> mappedBrAPIImport, Program program) throws ValidatorException {
        getExistingBrapiObjects(importRows, program);

        for (int i = 0; i < importRows.size(); i++) {
            BrAPIImport brapiImport = importRows.get(i);
            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(i, new PendingImport());

            Trial trial = brapiImport.getTrial();

            BrAPITrial brapiTrial = trial.constructBrAPITrial(program.getBrapiProgram());
            if (!trialByName.containsKey(trial.getTrialName())) {
                trialByName.put(brapiTrial.getTrialName(), new PendingImportObject<>(ImportObjectState.NEW, brapiTrial));
                mappedImportRow.setTrial(new PendingImportObject<>(ImportObjectState.NEW, brapiTrial));
            }
            mappedImportRow.setTrial(trialByName.get(trial.getTrialName()));
            mappedBrAPIImport.put(i, mappedImportRow);
        }

        ImportPreviewStatistics studyStats = ImportPreviewStatistics.builder()
                .newObjectCount(ProcessorData.getNumNewObjects(trialByName))
                .ignoredObjectCount(ProcessorData.getNumExistingObjects(trialByName))
                .build();

        return Map.of(NAME, studyStats);
    }

    @Override
    public void validateDependencies(Map<Integer, PendingImport> mappedBrAPIImport) throws ValidatorException {
        // no dependencies
    }

    @Override
    public void postBrapiData(Map<Integer, PendingImport> mappedBrAPIImport, Program program, ImportUpload upload) throws ValidatorException {
        List<BrAPITrial> trials = ProcessorData.getNewObjects(trialByName);

        List<BrAPITrial> createdTrials = new ArrayList<>();
        try {
            createdTrials.addAll(brapiTrialDAO.createBrAPITrial(trials, program.getId(), upload));
        } catch (ApiException e) {
            throw new InternalServerException(e.toString(), e);
        }

        // Update our records
        createdTrials.forEach(trial -> {
            PendingImportObject<BrAPITrial> preview = trialByName.get(trial.getTrialName());
            preview.setBrAPIObject(trial);
        });
    }

    @Override
    public String getName() {
        return NAME;
    }
}

