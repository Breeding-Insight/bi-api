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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;
import org.breedinginsight.brapps.importer.daos.BrAPIObservationDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIObservationVariableDAO;
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
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

@Prototype
public class ObservationProcessor implements Processor {

    private static final String NAME = "Observation";

    private BrAPIObservationVariableDAO brAPIVariableDAO;
    private BrAPIObservationDAO brAPIObservationDAO;
    private Map<Integer, PendingImportObject<BrAPIObservation>> observationByHash = new HashMap<>();
    private Map<String, BrAPIObservationVariable> variableByName = new HashMap<>();

    @Inject
    public ObservationProcessor(BrAPIObservationVariableDAO brAPIVariableDAO,
                                BrAPIObservationDAO brAPIObservationDAO) {
        this.brAPIVariableDAO = brAPIVariableDAO;
        this.brAPIObservationDAO = brAPIObservationDAO;
    }

    public void getExistingBrapiData(List<BrAPIImport> importRows, Program program) {
        // will skip existing observations, no error reported

        List<String> uniqueStudyNames = importRows.stream()
                .map(BrAPIImport::getObservations)
                .flatMap(Collection::stream)
                .map(observation -> observation.getStudy().getReferenceValue())
                .distinct()
                .collect(Collectors.toList());
        List<BrAPIObservation> existingObservations;

        Set<Integer> observationHashes = importRows.stream()
                .map(BrAPIImport::getObservations)
                .flatMap(Collection::stream)
                .map(observationImport -> getObservationHash(observationImport.getObservationUnit().getReferenceValue(),
                        observationImport.getTrait().getReferenceValue(),
                        observationImport.getObservationDate()))
                .collect(Collectors.toSet());

        try {
            existingObservations = brAPIObservationDAO.getObservationsByStudyName(uniqueStudyNames, program);
            existingObservations.forEach(existingObservation -> {
                int hash = getBrapiObservationHash(existingObservation);
                if (observationHashes.contains(hash)) {
                    observationByHash.put(hash, new PendingImportObject<>(ImportObjectState.EXISTING, existingObservation));
                }

            });
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }

        getDependentDbIds(importRows, program);
    }

    private void getDependentDbIds(List<BrAPIImport> importRows, Program program) {

        // TODO: any dependency not in import must already exist in brapi service
        if (!importRows.isEmpty()) {
            if (importRows.get(0).getGermplasm() == null) {
                // TODO: get and set germplasmDbId in all observations based on name lookup
            }
            if (importRows.get(0).getStudy() == null) {
                // TODO
            }
            if (importRows.get(0).getObservationUnit() == null) {
                // TODO
            }
            if (importRows.get(0).getObservationVariable() == null) {
                List<String> uniqueVariableNames = importRows.stream()
                        .map(BrAPIImport::getObservations)
                        .flatMap(Collection::stream)
                        .map(observation -> observation.getTrait().getReferenceValue())
                        .distinct()
                        .collect(Collectors.toList());
                List<BrAPIObservationVariable> existingVariables;

                try {
                    existingVariables = brAPIVariableDAO.getVariableByName(uniqueVariableNames, program.getId());
                } catch (ApiException e) {
                    // We shouldn't get an error back from our services. If we do, nothing the user can do about it
                    throw new InternalServerException(e.toString(), e);
                }

                Set<String> names = existingVariables.stream().map(BrAPIObservationVariable::getObservationVariableName).collect(toSet());
                if (!names.containsAll(uniqueVariableNames)) {
                    throw new HttpStatusException(HttpStatus.NOT_FOUND, "Observation variables must exist in brapi service");
                }

                existingVariables.forEach(existingVariable -> {
                    variableByName.put(existingVariable.getObservationVariableName(), existingVariable);
                });
            }
        }
    }

    @Override
    public Map<String, ImportPreviewStatistics> process(List<BrAPIImport> importRows, Map<Integer, PendingImport> mappedBrAPIImport, Program program) throws ValidatorException {

        if (!importRows.isEmpty() && importRows.get(0).getObservations() != null) {

            for (int i = 0; i < importRows.size(); i++) {
                BrAPIImport brapiImport = importRows.get(i);
                PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(i, new PendingImport());

                List<Observation> observations = brapiImport.getObservations();

                for (Observation observation : observations) {
                    BrAPIObservationVariable variable = variableByName.get(observation.getTrait().getReferenceValue());
                    BrAPIObservation brapiObservation = observation.constructBrAPIObservation();
                    brapiObservation.setObservationVariableDbId(variable.getObservationVariableDbId());

                    int hash = getObservationHash(observation.getObservationUnit().getReferenceValue(),
                            variable.getObservationVariableName(),
                            observation.getObservationDate());
                    if (!observationByHash.containsKey(hash)) {
                        observationByHash.put(hash, new PendingImportObject<>(ImportObjectState.NEW, brapiObservation));
                        mappedImportRow.setObservation(new PendingImportObject<>(ImportObjectState.NEW, brapiObservation));
                    }
                    mappedImportRow.setObservation(observationByHash.get(hash));
                    mappedBrAPIImport.put(i, mappedImportRow);
                }

            }
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

        List<BrAPIObservation> createdObservations = new ArrayList<>();
        try {
            createdObservations.addAll(brAPIObservationDAO.createBrAPIObservation(observations, program.getId(), upload));
        } catch (ApiException e) {
            throw new InternalServerException(e.toString(), e);
        }

        // Update our records
        createdObservations.forEach(observation -> {
            int hash = getBrapiObservationHash(observation);
            PendingImportObject<BrAPIObservation> preview = observationByHash.get(hash);
            preview.setBrAPIObject(observation);

        });
    }

    private void updateDependencyValues(Map<Integer, PendingImport> mappedBrAPIImport) {
        // update study DbIds
        mappedBrAPIImport.values().stream()
                .map(PendingImport::getStudy)
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(this::updateStudyDbId);

        // update observation unit DbIds
        mappedBrAPIImport.values().stream()
                .map(PendingImport::getObservationUnit)
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(this::updateObservationUnitDbId);

        // variables must already exist so don't need to update

    }

    private void updateStudyDbId(BrAPIStudy study) {
        observationByHash.values().stream()
                .filter(obs -> obs.getBrAPIObject().getStudyDbId().equals(study.getStudyName()))
                .forEach(obs -> obs.getBrAPIObject().setStudyDbId(study.getStudyDbId()));
    }

    private void updateObservationUnitDbId(BrAPIObservationUnit observationUnit) {
        observationByHash.values().stream()
                .filter(obs -> obs.getBrAPIObject().getObservationUnitName().equals(observationUnit.getObservationUnitName()))
                .forEach(obs -> obs.getBrAPIObject().setObservationUnitDbId(observationUnit.getObservationUnitDbId()));
    }

    private static int getBrapiObservationHash(BrAPIObservation observation) {
        return getObservationHash(observation.getObservationUnitName(),
               observation.getObservationVariableName(),
               observation.getObservationTimeStamp().withOffsetSameInstant(ZoneOffset.UTC).format(Observation.formatter));
    }

    private static int getObservationHash(String observationUnitName, String variableName, String observationDate) {
        return Objects.hash(observationUnitName, variableName, observationDate);
    }

    @Override
    public String getName() {
        return NAME;
    }
}
