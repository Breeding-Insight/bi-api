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
import org.brapi.v2.model.core.BrAPILocation;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapps.importer.daos.BrAPIStudyDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.base.Study;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.ValidatorException;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Prototype
public class StudyProcessor implements Processor {

    private static final String NAME = "Study";

    private BrAPIStudyDAO brAPIStudyDAO;
    private Map<String, PendingImportObject<BrAPIStudy>> studyByName = new HashMap<>();

    @Inject
    public StudyProcessor(BrAPIStudyDAO brAPIStudyDAO) {
        this.brAPIStudyDAO = brAPIStudyDAO;
    }

    private void getExistingBrapiObjects(List<BrAPIImport> importRows, Program program) {

        // get unique study names
        List<String> uniqueStudyNames = importRows.stream()
                .map(studyImport -> studyImport.getStudy().getStudyName())
                .distinct()
                .collect(Collectors.toList());

        List<BrAPIStudy> existingStudies;

        try {
            existingStudies = brAPIStudyDAO.getStudyByName(uniqueStudyNames, program);
            existingStudies.forEach(existingStudy -> {
                studyByName.put(existingStudy.getStudyName(), new PendingImportObject<>(ImportObjectState.EXISTING, existingStudy));
            });
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }

    }

    @Override
    public Map<String, ImportPreviewStatistics> process(List<BrAPIImport> importRows, Map<Integer, PendingImport> mappedBrAPIImport, Program program) {

        getExistingBrapiObjects(importRows, program);

        for (int i = 0; i < importRows.size(); i++) {
            BrAPIImport brapiImport = importRows.get(i);
            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(i, new PendingImport());

            Study study = brapiImport.getStudy();

            BrAPIStudy brapiStudy = study.constructBrAPIStudy();
            if (!studyByName.containsKey(study.getStudyName())) {
                studyByName.put(brapiStudy.getStudyName(), new PendingImportObject<>(ImportObjectState.NEW, brapiStudy));
                mappedImportRow.setStudy(new PendingImportObject<>(ImportObjectState.NEW, brapiStudy));
            }
            mappedImportRow.setStudy(studyByName.get(study.getStudyName()));
            mappedBrAPIImport.put(i, mappedImportRow);
        }

        ImportPreviewStatistics studyStats = ImportPreviewStatistics.builder()
                .newObjectCount(ProcessorData.getNumNewObjects(studyByName))
                .ignoredObjectCount(ProcessorData.getNumExistingObjects(studyByName))
                .build();

        return Map.of(NAME, studyStats);
    }

    @Override
    public void validateDependencies(Map<Integer, PendingImport> mappedBrAPIImport) throws ValidatorException {
        // TODO: validate dependencies
    }

    @Override
    public void postBrapiData(Map<Integer, PendingImport> mappedBrAPIImport, Program program, ImportUpload upload) {

        // check shared data object for dependency data and update observation units
        updateDependencyValues(mappedBrAPIImport);

        List<BrAPIStudy> studies = ProcessorData.getNewObjects(studyByName);

        // POST Study
        List<BrAPIStudy> createdStudies = new ArrayList<>();
        try {
            createdStudies.addAll(brAPIStudyDAO.createBrAPIStudy(studies, program.getId(), upload));
        } catch (ApiException e) {
            throw new InternalServerException(e.toString(), e);
        }

        // Update our records
        createdStudies.forEach(study -> {
            PendingImportObject<BrAPIStudy> preview = studyByName.get(study.getStudyName());
            preview.setBrAPIObject(study);
        });

    }

    private void updateDependencyValues(Map<Integer, PendingImport> mappedBrAPIImport) {
        // update location DbIds in studies for all distinct locations
        mappedBrAPIImport.values().stream()
                .map(PendingImport::getLocation)
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(this::updateLocationDbId);

        // update trial DbIds in studies for all distinct trials
        mappedBrAPIImport.values().stream()
                .map(PendingImport::getTrial)
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(this::updateTrialDbId);
    }

    private void updateLocationDbId(BrAPILocation location) {
        studyByName.values().stream()
                .filter(study -> study.getBrAPIObject().getLocationName().equals(location.getLocationName()))
                .forEach(study -> study.getBrAPIObject().setLocationDbId(location.getLocationDbId()));
    }

    private void updateTrialDbId(BrAPITrial trial) {
        studyByName.values().stream()
                .filter(study -> study.getBrAPIObject().getTrialName().equals(trial.getTrialName()))
                .forEach(study -> study.getBrAPIObject().setTrialDbId(trial.getTrialDbId()));
    }

    @Override
    public String getName() {
        return NAME;
    }

}
