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
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.daos.BrAPIObservationDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.base.Observation;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.ValidatorException;

import javax.inject.Inject;
import java.util.*;

@Prototype
public class ObservationProcessor implements Processor {

    private static final String NAME = "Observation";

    private BrAPIObservationDAO brAPIObservationDAO;
    private Map<Observation, PendingImportObject<BrAPIObservation>> observationByHash = new HashMap<>();

    @Inject
    public ObservationProcessor(BrAPIObservationDAO brAPIObservationDAO) {
        this.brAPIObservationDAO = brAPIObservationDAO;
    }

    private void checkExistingObservations(List<BrAPIImport> importRows, Program program) {
        // TODO: check according to breedbase rules and report issues
    }

    private void getDependentDbIds(List<BrAPIImport> importRows) {

        // TODO: any dependency not in import must already exist is service
        if (!importRows.isEmpty()) {
            if (importRows.get(0).getGermplasm() == null) {
                // get and set germplasmDbId in all observations based on name lookup
            }
            if (importRows.get(0).getStudy() == null) {

            }
            if (importRows.get(0).getObservationUnit() == null) {

            }
            if (importRows.get(0).getObservationVariable() == null) {

            }
        }
    }

    @Override
    public Map<String, ImportPreviewStatistics> process(List<BrAPIImport> importRows, Map<Integer, PendingImport> mappedBrAPIImport, Program program) throws ValidatorException {

        checkExistingObservations(importRows, program);

        getDependentDbIds(importRows);

        for (int i = 0; i < importRows.size(); i++) {
            BrAPIImport brapiImport = importRows.get(i);
            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(i, new PendingImport());

            Observation observation = brapiImport.getObservation();
            BrAPIObservation brapiObservation = observation.constructBrAPIObservation();

            if (!observationByHash.containsKey(observation)) {
                observationByHash.put(observation, new PendingImportObject<>(ImportObjectState.NEW, brapiObservation));
                mappedImportRow.setObservation(new PendingImportObject<>(ImportObjectState.NEW, brapiObservation));
            }
            mappedImportRow.setObservation(observationByHash.get(observation));
            mappedBrAPIImport.put(i, mappedImportRow);
        }

        ImportPreviewStatistics stats = ImportPreviewStatistics.builder()
                .newObjectCount(ProcessorData.getNumNewObjects(observationByHash))
                .ignoredObjectCount(ProcessorData.getNumExistingObjects(observationByHash))
                .build();

        return Map.of(NAME, stats);
    }

    @Override
    public void validateDependencies(Map<Integer, PendingImport> mappedBrAPIImport) throws ValidatorException {
        // TODO
    }

    @Override
    public void postBrapiData(Map<Integer, PendingImport> mappedBrAPIImport, Program program, ImportUpload upload) throws ValidatorException {
        // check shared data object for dependency data and update observation units
        updateDependencyValues(mappedBrAPIImport);

        List<BrAPIObservation> observations = ProcessorData.getNewObjects(observationByHash);

        // POST Study
        List<BrAPIObservation> createdObservations = new ArrayList<>();
        try {
            createdObservations.addAll(brAPIObservationDAO.createBrAPIObservation(observations, program.getId(), upload));
        } catch (ApiException e) {
            throw new InternalServerException(e.toString(), e);
        }

        // Update our records
        createdObservations.forEach(observation -> {
            PendingImportObject<BrAPIObservation> preview = observationByHash.get(Observation.observationFromBrapiObservation(observation));
            preview.setBrAPIObject(observation);
        });
    }

    private void updateDependencyValues(Map<Integer, PendingImport> mappedBrAPIImport) {
        // TODO
    }

    @Override
    public String getName() {
        return NAME;
    }
}
