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
package org.breedinginsight.brapps.importer.model.imports.study;

import io.micronaut.http.server.exceptions.InternalServerException;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.daos.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIProgramDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIStudyDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.base.ObservationUnit;
import org.breedinginsight.brapps.importer.model.base.Study;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImportService;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.FileMappingUtil;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class StudyImportService extends BrAPIImportService {

    private String IMPORT_TYPE_ID = "StudyImport";

    private BrAPIStudyDAO brAPIStudyDAO;
    private BrAPIProgramDAO brAPIProgramDAO;
    private BrAPIObservationUnitDAO brAPIObservationUnitDAO;
    private FileMappingUtil fileMappingUtil;

    @Inject
    public StudyImportService(FileMappingUtil fileMappingUtil,
                              BrAPIProgramDAO brAPIProgramDAO,
                              BrAPIStudyDAO brAPIStudyDAO,
                              BrAPIObservationUnitDAO braAPIObservationUnitDAO)
    {
        this.fileMappingUtil = fileMappingUtil;
        this.brAPIStudyDAO = brAPIStudyDAO;
        this.brAPIProgramDAO = brAPIProgramDAO;
        this.brAPIObservationUnitDAO = braAPIObservationUnitDAO;
    }

    @Override
    public StudyImport getImportClass() {
        return new StudyImport();
    }

    @Override
    public String getImportTypeId() {
        return IMPORT_TYPE_ID;
    }

    @Override
    public ImportPreviewResponse process(List<BrAPIImport> brAPIImports, Table data, Program program, ImportUpload upload, Boolean commit)
            throws UnprocessableEntityException {

        // brapi objects per row
        List<StudyImport> studyImports = (List<StudyImport>)(List<?>) brAPIImports;
        Map<Integer, StudyImportPending> mappedBrAPIImport = new HashMap<>();

        Map<String, PendingImportObject<BrAPIStudy>> studyByName = new HashMap<>();
        Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByName = new HashMap<>();

        ImportPreviewStatistics studyStats = processStudies(studyImports, mappedBrAPIImport, studyByName);
        ImportPreviewStatistics observationUnitStats = processObservationUnits(studyImports, mappedBrAPIImport, observationUnitByName);

        // get our new objects to create
        List<BrAPIStudy> newStudyList = studyByName.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.NEW)
                .map(preview -> preview.getBrAPIObject())
                .collect(Collectors.toList());

        List<BrAPIObservationUnit> newObservationUnitList = observationUnitByName.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.NEW)
                .map(preview -> preview.getBrAPIObject())
                .collect(Collectors.toList());

        ImportPreviewResponse response = new ImportPreviewResponse();

        response.setStatistics(Map.of(
                "Study", studyStats,
                "Observation Unit", observationUnitStats
        ));

        List<StudyImportPending> mappedBrAPIImportList = mappedBrAPIImport.values().stream().collect(Collectors.toList());
        response.setRows((List<PendingImport>)(List<?>) mappedBrAPIImportList);

        if (!commit) {
            return response;
        } else {
            postStudies(newStudyList, studyByName);
            postObservationUnits(newObservationUnitList, observationUnitByName);
        }

        return response;
    }

    private ImportPreviewStatistics processStudies(List<StudyImport> studyImports,
                                                   Map<Integer, StudyImportPending> mappedBrAPIImport,
                                                   Map<String, PendingImportObject<BrAPIStudy>> studyByName) {
        // get unique study names
        List<String> uniqueStudyNames = studyImports.stream()
                .map(studyImport -> studyImport.getStudy().getStudyName())
                .distinct()
                .collect(Collectors.toList());

        // check for existing studies. Don't want to update existing, just create new.
        // TODO: do we allow adding observation units/observations to existing studies? for now, no.
        // ignore all data for studies existing in system
        List<BrAPIStudy> existingStudies;

        try {
            existingStudies = brAPIStudyDAO.getStudyByName(uniqueStudyNames);
            existingStudies.forEach(existingStudy -> {
                studyByName.put(existingStudy.getStudyName(), new PendingImportObject<>(ImportObjectState.EXISTING, existingStudy));
            });
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }

        for (int i = 0; i < studyImports.size(); i++) {
            StudyImport studyImport = studyImports.get(i);

            StudyImportPending mappedImportRow;
            if (mappedBrAPIImport.containsKey(i)) {
                mappedImportRow = mappedBrAPIImport.get(i);
            } else {
                mappedImportRow = new StudyImportPending();
            }

            Study study = studyImport.getStudy();
            BrAPIStudy brapiStudy = study.constructBrAPIStudy();
            if (!studyByName.containsKey(study.getStudyName())) {
                studyByName.put(brapiStudy.getStudyName(), new PendingImportObject<>(ImportObjectState.NEW, brapiStudy));
                mappedImportRow.setStudy(new PendingImportObject<>(ImportObjectState.NEW, brapiStudy));
            }
            mappedImportRow.setStudy(studyByName.get(study.getStudyName()));
            mappedBrAPIImport.put(i, mappedImportRow);
        }

        long numNewStudies = studyByName.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.NEW)
                .count();
        int numNewStudiesInt = Math.toIntExact(numNewStudies);

        long numIgnoredStudies = studyByName.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.EXISTING)
                .count();
        int numIgnoredStudiesInt = Math.toIntExact(numIgnoredStudies);
        ImportPreviewStatistics studyStats = new ImportPreviewStatistics();
        studyStats.setNewObjectCount(numNewStudiesInt);
        studyStats.setIgnoredObjectCount(numIgnoredStudiesInt);

        return studyStats;
    }

    private void postStudies(List<BrAPIStudy> studies, Map<String, PendingImportObject<BrAPIStudy>> studyByName) {
        // POST Study
        List<BrAPIStudy> createdStudies = new ArrayList<>();
        try {
            createdStudies.addAll(brAPIStudyDAO.createBrAPIStudy(studies));
        } catch (ApiException e) {
            throw new InternalServerException(e.toString(), e);
        }

        // Update our records
        createdStudies.forEach(study -> {
            PendingImportObject<BrAPIStudy> preview = studyByName.get(study.getStudyName());
            preview.setBrAPIObject(study);
        });
    }

    // these could potentially be generalized
    private ImportPreviewStatistics processObservationUnits(List<StudyImport> studyImports,
                                                            Map<Integer, StudyImportPending> mappedBrAPIImport,
                                                            Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByName) {
        // get unique observation unit names
        List<String> uniqueObservationUnitNames = studyImports.stream()
                .map(studyImport -> studyImport.getObservationUnit().getObservationUnitName())
                .distinct()
                .collect(Collectors.toList());

        // check for existing observation units. Don't want to update existing, just create new.
        // TODO: do we allow adding observations to existing studies? yes, but not updating
        // ignore all data for observation units existing in system
        List<BrAPIObservationUnit> existingObservationUnits;

        try {
            existingObservationUnits = brAPIObservationUnitDAO.getObservationUnitByName(uniqueObservationUnitNames);
            existingObservationUnits.forEach(existingObservationUnit -> {
                observationUnitByName.put(existingObservationUnit.getObservationUnitName(), new PendingImportObject<>(ImportObjectState.EXISTING, existingObservationUnit));
            });
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }

        for (int i = 0; i < studyImports.size(); i++) {
            StudyImport studyImport = studyImports.get(i);

            StudyImportPending mappedImportRow;
            if (mappedBrAPIImport.containsKey(i)) {
                mappedImportRow = mappedBrAPIImport.get(i);
            } else {
                mappedImportRow = new StudyImportPending();
            }

            ObservationUnit observationUnit = studyImport.getObservationUnit();
            BrAPIObservationUnit brapiObservationUnit = observationUnit.constructBrAPIObservationUnit();
            if (!observationUnitByName.containsKey(observationUnit.getObservationUnitName())) {
                observationUnitByName.put(brapiObservationUnit.getObservationUnitName(), new PendingImportObject<>(ImportObjectState.NEW, brapiObservationUnit));
                mappedImportRow.setObservationUnit(new PendingImportObject<>(ImportObjectState.NEW, brapiObservationUnit));
            }
            mappedImportRow.setObservationUnit(observationUnitByName.get(observationUnit.getObservationUnitName()));
            mappedBrAPIImport.put(i, mappedImportRow);
        }

        long numNewObservationUnits = observationUnitByName.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.NEW)
                .count();
        int numNewObservationUnitsInt = Math.toIntExact(numNewObservationUnits);

        long numIgnoredObservationUnits = observationUnitByName.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.EXISTING)
                .count();
        int numIgnoredObservationUnitsInt = Math.toIntExact(numIgnoredObservationUnits);
        ImportPreviewStatistics observationUnitStats = new ImportPreviewStatistics();
        observationUnitStats.setNewObjectCount(numNewObservationUnitsInt);
        observationUnitStats.setIgnoredObjectCount(numIgnoredObservationUnitsInt);

        return observationUnitStats;
    }

    private void postObservationUnits(List<BrAPIObservationUnit> observationUnits, Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByName) {
        List<BrAPIObservationUnit> createdObservationUnits = new ArrayList<>();
        try {
            createdObservationUnits.addAll(brAPIObservationUnitDAO.createBrAPIObservationUnits(observationUnits));
        } catch (ApiException e) {
            throw new InternalServerException(e.toString(), e);
        }

        // Update our records
        createdObservationUnits.forEach(observationUnit -> {
            PendingImportObject<BrAPIObservationUnit> preview = observationUnitByName.get(observationUnit.getObservationUnitName());
            preview.setBrAPIObject(observationUnit);
        });
    }


}

