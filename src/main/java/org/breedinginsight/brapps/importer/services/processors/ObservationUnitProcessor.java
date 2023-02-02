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
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.daos.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.base.ObservationUnit;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.ValidatorException;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Prototype
public class ObservationUnitProcessor implements Processor {

    private static final String NAME = "Observation unit";

    private BrAPIObservationUnitDAO brAPIObservationUnitDAO;
    private Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByName = new HashMap<>();
    private Set<String> studyNames = new HashSet();
    private static final Set<String> ALLOWED_LEVELS = Set.of("plot", "plant");

    @Inject
    public ObservationUnitProcessor(BrAPIObservationUnitDAO brAPIObservationUnitDAO) {
        this.brAPIObservationUnitDAO = brAPIObservationUnitDAO;
    }

    public void getExistingBrapiData(List<BrAPIImport> importRows, Program program) {

        // get unique observation unit names
        List<String> uniqueObservationUnitNames = importRows.stream()
                .map(studyImport -> studyImport.getObservationUnit().getObservationUnitName())
                .distinct()
                .collect(Collectors.toList());

        // check for existing observation units. Don't want to update existing, just create new.
        // TODO: do we allow adding observations to existing studies? yes, but not updating
        // ignore all data for observation units existing in system

        List<BrAPIObservationUnit> existingObservationUnits;

        try {
            existingObservationUnits = brAPIObservationUnitDAO.getObservationUnitByName(uniqueObservationUnitNames, program);
            existingObservationUnits.forEach(existingObservationUnit -> {

                // update mapped brapi import, does in process
                observationUnitByName.put(existingObservationUnit.getObservationUnitName(), new PendingImportObject<>(ImportObjectState.EXISTING, existingObservationUnit));
            });
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }
    }

    private void getExistingStudyObjects() {

    }

    @Override
    public Map<String, ImportPreviewStatistics> process(List<BrAPIImport> importRows,
                                                        Map<Integer,PendingImport> mappedBrAPIImport, Table data,
                                                        Program program, User user, boolean commit) {

        for (int i = 0; i < importRows.size(); i++) {
            BrAPIImport brapiImport = importRows.get(i);
            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(i, new PendingImport());

            ObservationUnit observationUnit = brapiImport.getObservationUnit();
            if (observationUnit == null) {
                throw new IllegalArgumentException("Import profile must have observation unit for observation unit processor");
            }

            BrAPIObservationUnit brapiObservationUnit = observationUnit.constructBrAPIObservationUnit();

            String levelName = brapiObservationUnit.getObservationUnitPosition().getObservationLevel().getLevelName().toLowerCase();
            if (!ALLOWED_LEVELS.contains(levelName)) {
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Observation unit level name not allowed");
            }
            brapiObservationUnit.getObservationUnitPosition().getObservationLevel().setLevelName(levelName);

            if (!observationUnitByName.containsKey(observationUnit.getObservationUnitName())) {
                observationUnitByName.put(brapiObservationUnit.getObservationUnitName(), new PendingImportObject<>(ImportObjectState.NEW, brapiObservationUnit));
                mappedImportRow.setObservationUnit(new PendingImportObject<>(ImportObjectState.NEW, brapiObservationUnit));
            }
            mappedImportRow.setObservationUnit(observationUnitByName.get(observationUnit.getObservationUnitName()));
            mappedBrAPIImport.put(i, mappedImportRow);
        }

        ImportPreviewStatistics stats = ImportPreviewStatistics.builder()
                .newObjectCount(ProcessorData.getNumNewObjects(observationUnitByName))
                .ignoredObjectCount(ProcessorData.getNumExistingObjects(observationUnitByName))
                .build();

        return Map.of(NAME, stats);
    }

    @Override
    public void validateDependencies(Map<Integer, PendingImport> mappedBrAPIImport) throws ValidatorException {
        // TODO: check if have studies, etc.
    }


    @Override
    public void postBrapiData(Map<Integer, PendingImport> mappedBrAPIImport, Program program, ImportUpload upload) {

        // check shared data object for dependency data and update observation units
        updateDependencyValues(mappedBrAPIImport);

        List<BrAPIObservationUnit> observationUnits = ProcessorData.getNewObjects(observationUnitByName);

        List<BrAPIObservationUnit> createdObservationUnits = new ArrayList<>();
        try {
            createdObservationUnits.addAll(brAPIObservationUnitDAO.createBrAPIObservationUnits(observationUnits, program.getId(), upload));
        } catch (ApiException e) {
            throw new InternalServerException(e.toString(), e);
        }

        // Update our records
        createdObservationUnits.forEach(observationUnit -> {
            // update mapped brapi import, will have to do either way? no
            PendingImportObject<BrAPIObservationUnit> preview = observationUnitByName.get(observationUnit.getObservationUnitName());
            preview.setBrAPIObject(observationUnit);
            updateObservationUnitDbIds(mappedBrAPIImport);
        });

    }

    private void updateObservationUnitDbIds(Map<Integer, PendingImport> mappedBrAPIImport) {
        observationUnitByName.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.NEW)
                .map(PendingImportObject::getBrAPIObject)
                .forEach(p -> updateObservationUnitDbId(mappedBrAPIImport, p));
    }

    private void updateObservationUnitDbId(Map<Integer, PendingImport> mappedBrAPIImport, BrAPIObservationUnit observationUnit) {
        mappedBrAPIImport.values().stream()
                .filter(obsUnit -> obsUnit.getObservationUnit()
                        .getBrAPIObject().getObservationUnitName().equals(observationUnit.getObservationUnitName()))
                .forEach(obsUnit -> obsUnit.getObservationUnit().getBrAPIObject().setObservationUnitDbId(observationUnit.getObservationUnitDbId()));
    }

    private void updateDependencyValues(Map<Integer, PendingImport> mappedBrAPIImport) {

        // update study DbIds
        mappedBrAPIImport.values().stream()
                .map(PendingImport::getStudy)
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(this::updateStudyDbId);

        // update germplasm DbIds
        mappedBrAPIImport.values().stream()
                .map(PendingImport::getGermplasm)
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(this::updateGermplasmDbId);

    }

    private void updateStudyDbId(BrAPIStudy study) {
        observationUnitByName.values().stream()
                .filter(obsUnit -> obsUnit.getBrAPIObject().getStudyName().equals(study.getStudyName()))
                .forEach(obsUnit -> obsUnit.getBrAPIObject().setStudyDbId(study.getStudyDbId()));
    }

    private void updateGermplasmDbId(BrAPIGermplasm germplasm) {
        observationUnitByName.values().stream()
                .filter(obsUnit -> obsUnit.getBrAPIObject().getGermplasmName() != null &&
                        obsUnit.getBrAPIObject().getGermplasmName().equals(germplasm.getGermplasmName()))
                .forEach(obsUnit -> obsUnit.getBrAPIObject().setGermplasmDbId(germplasm.getGermplasmDbId()));
    }

    @Override
    public String getName() {
        return NAME;
    }


}
