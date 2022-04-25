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

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
//import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPILocation;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.pheno.*;
import org.breedinginsight.brapps.importer.daos.BrAPILocationDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIStudyDAO;
import org.breedinginsight.brapps.importer.daos.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.*;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.utilities.Utilities;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Prototype
public class ExperimentProcessor implements Processor {

    private static final String NAME = "Experiment";

    private FileData fileData = new FileData();

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    private DSLContext dsl;
    private BrAPITrialDAO brapiTrialDAO;
    private BrAPILocationDAO brAPILocationDAO;
    private BrAPIStudyDAO brAPIStudyDAO;
    private BrAPIObservationUnitDAO brAPIObservationUnitDAO;

    private List<BrAPITrial> newTrialsList = new ArrayList<>();
    private List<BrAPILocation> locationList = new ArrayList<>();
    private List<BrAPIStudy> studyList = new ArrayList<>();
    private List<BrAPIObservationUnit> observationUnitList = new ArrayList<>();

    private Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope = new HashMap<>();
    private Map<String, PendingImportObject<BrAPILocation>> locationByName = new HashMap<>();
    private Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope = new HashMap<>();
    private Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope = new HashMap<>();

    @Inject
    public ExperimentProcessor(DSLContext dsl, BrAPITrialDAO brapiTrialDAO, BrAPILocationDAO brAPILocationDAO, BrAPIStudyDAO brAPIStudyDAO, BrAPIObservationUnitDAO brAPIObservationUnitDAO) {
        this.dsl = dsl;
        this.brapiTrialDAO = brapiTrialDAO;
        this.brAPILocationDAO = brAPILocationDAO;
        this.brAPIStudyDAO = brAPIStudyDAO;
        this.brAPIObservationUnitDAO = brAPIObservationUnitDAO;
    }

    public ExperimentObservation getSupportedImport(){
        return new ExperimentObservation();
    }

    public void getExistingBrapiData(List<BrAPIImport> importRows, Program program) {

        List<ExperimentObservation> experimentImportRows = importRows.stream()
                .map(trialImport -> (ExperimentObservation) trialImport)
                .collect(Collectors.toList());

        // Trials
        List<String> uniqueTrialNames = experimentImportRows.stream()
                .map(experimentImport -> experimentImport.getExpTitle())
                .distinct()
                .collect(Collectors.toList());
        List<BrAPITrial> existingTrials;

        try {
            existingTrials = brapiTrialDAO.getTrialByName(uniqueTrialNames, program);
            existingTrials.forEach(existingTrial -> {
                trialByNameNoScope.put(
                        Utilities.removeProgramKey(existingTrial.getTrialName(), program.getKey()),
                        new PendingImportObject<>(ImportObjectState.EXISTING, existingTrial));
            });
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }

        // Locations
        List<String> uniqueLocationNames = experimentImportRows.stream()
                .map(experimentImport -> experimentImport.getEnvLocation())
                .distinct()
                .collect(Collectors.toList());

        List<BrAPILocation> existingLocations;
        try {
            existingLocations = brAPILocationDAO.getLocationsByName(uniqueLocationNames, program.getId());
            existingLocations.forEach(existingLocation -> {
                locationByName.put(existingLocation.getLocationName(), new PendingImportObject<>(ImportObjectState.EXISTING, existingLocation));
            });
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }

        // Studies
        List<String> uniqueStudyNames = experimentImportRows.stream()
                .map(experimentImport -> experimentImport.getEnv())
                .distinct()
                .collect(Collectors.toList());

        List<BrAPIStudy> existingStudies;
        try {
            existingStudies = brAPIStudyDAO.getStudyByName(uniqueStudyNames, program);
            existingStudies.forEach(existingStudy -> {
                String studySequence = null;
                if ((existingStudy.getAdditionalInfo()!=null) && (existingStudy.getAdditionalInfo().get("studySequence") != null )) {
                    studySequence = existingStudy.getAdditionalInfo().get("studySequence").getAsString();
                }
                studyByNameNoScope.put(
                        Utilities.removeProgramKey(existingStudy.getStudyName(), program.getKey(), studySequence),
                        new PendingImportObject<>(ImportObjectState.EXISTING, existingStudy));
            });
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }

        // Observation Unit
        List<String> uniqueObservationUnitNames = experimentImportRows.stream()
                .map(experimentImport -> experimentImport.getExpUnitId())
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
                observationUnitByNameNoScope.put(existingObservationUnit.getObservationUnitName(), new PendingImportObject<>(ImportObjectState.EXISTING, existingObservationUnit));
            });
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }
    }

    @Override
    public void validate(List<BrAPIImport> importRows, Program program) throws ValidatorException, ApiException {

    }

    @Override
    public void process(List<BrAPIImport> importRows, Program program, User user, boolean commit,
                            Map<Integer, PendingImport> mappedBrAPIImport, Map<String, ImportPreviewStatistics> importStatistics
                        ) throws ValidatorException {

        // Validations
        // GID for existing germplasm. Throw error if GID not found.
        // Test or Check, bad letter throw an error.

        //TODO retrieve sequanceName from the program table.
        String studySequenceName = "";
        Supplier<BigInteger> studyNextVal = () -> dsl.nextval(studySequenceName.toLowerCase());
        String opsUnitSequenceName = "";
        Supplier<BigInteger> obsUnitNextVal = () -> dsl.nextval(opsUnitSequenceName.toLowerCase());

        // For each import row
        for (int i = 0; i < importRows.size(); i++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(i);
            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(i, new PendingImport());
            // Construct Trial
            // Unique by trialName

            if( trialByNameNoScope.containsKey( importRow.getExpTitle()) ) {
                mappedImportRow.setTrial( trialByNameNoScope.get( importRow.getExpTitle() ) );
            }
            else {
                BrAPITrial newTrial = importRow.constructBrAPITrial(program, commit);
                PendingImportObject newImportObject = new PendingImportObject<>(ImportObjectState.NEW, newTrial);
                trialByNameNoScope.put(importRow.getExpTitle(), newImportObject);
                newTrialsList.add(newTrial);
                mappedImportRow.setTrial( newImportObject );
            }

            if( locationByName.containsKey(( importRow.getEnvLocation() ))){
                mappedImportRow.setLocation( locationByName.get( importRow.getEnvLocation() ) );
            }
            else{
                BrAPILocation newLocation = importRow.constructBrAPILocation();
                PendingImportObject newImportObject = new PendingImportObject<>(ImportObjectState.NEW, newLocation);
                locationByName.put(importRow.getEnvLocation(), newImportObject);
                mappedImportRow.setLocation( newImportObject );
            }

            if( studyByNameNoScope.containsKey( importRow.getEnv()) ) {
                mappedImportRow.setStudy( studyByNameNoScope.get( importRow.getEnv() ) );
            }
            else{
                BrAPIStudy newStudy = importRow.constructBrAPIStudy(program, studyNextVal, commit );
                PendingImportObject newImportObject = new PendingImportObject<>(ImportObjectState.NEW, newStudy);
                studyByNameNoScope.put(importRow.getEnv(),newImportObject);
                mappedImportRow.setStudy( newImportObject );
            }

            if( observationUnitByNameNoScope.containsKey( importRow.getExpUnitId() ) ) {
                mappedImportRow.setObservationUnit( observationUnitByNameNoScope.get(importRow.getExpUnitId() ) );
            }
            else{
                BrAPIObservationUnit newObservationUnit = importRow.constructBrAPIObservationUnit(program, obsUnitNextVal, commit );
                PendingImportObject newImportObject = new PendingImportObject<>(ImportObjectState.NEW, newObservationUnit);
                observationUnitByNameNoScope.put(importRow.getExpUnitId(), new PendingImportObject<>(ImportObjectState.NEW, newObservationUnit));
                mappedImportRow.setObservationUnit( newImportObject );
            }

            // Construct Observation Unit
            // Manual test in breedbase to see observation level of any type supported
            // Are we limiting to just plot and plant?

            // Construct Observations -- Done in another card
            mappedBrAPIImport.put(i, mappedImportRow);
        }

        // TODO need to add things to newExperimentList
        // Construct our response object
        ImportPreviewStatistics experimentStats = ImportPreviewStatistics.builder()
                .newObjectCount(1)
                .build();



        //TODO What do we what mapped
        importStatistics.put("Experiment", experimentStats);
    }

    private void extracted() {
        for (ExperimentData experimentData : this.fileData.experimentData()) {
            // ?? Use BrAPITrial.constructBrAPITrial(BrAPIProgram brapiProgram)
            BrAPITrial brAPITrial = new BrAPITrial();
            //Exp Title → Trial.trialName
            brAPITrial.setTrialName(experimentData.getTitle());
            // Exp Description → Trial.trialDescription
            brAPITrial.setTrialDescription(experimentData.getDescription());

            //observationUnit →
            for (EnvironmentData environmentData : experimentData.environmentData_values()) {
                BrAPIStudy brAPIStudy = new BrAPIStudy();
                // TODO How dose this map?
                // Exp Type → Study.studyType
                // brAPIStudy.setStudyType( environment.);

                // Env → Study.studyName
                brAPIStudy.setStudyName(environmentData.getEnv());

                // TODO How does this map? brAPIStudy.setSeasons(seasons) wants a List<String> for seasons
                // Env Year → Study.seasons  (This field must be numeric, and be a four digit year)
                //brAPIStudy.setSeasons();

                // Env Location → Study.locationDbId
                brAPIStudy.setLocationDbId(environmentData.getLocation());
                // TODO lookup by name (getDbid) or else create a new Location

                for (ObservationUnitData observationUnitData : environmentData.getObservationUnitDataList()) {
                    BrAPIObservationUnitLevelRelationship brAPIobsUnitLevel = new BrAPIObservationUnitLevelRelationship();
                    BrAPIObservationUnitPosition brAPIobsUnitPos = new BrAPIObservationUnitPosition();
                    BrAPIObservationUnit brAPIobservationUnit = new BrAPIObservationUnit();
                    brAPIobsUnitPos.setObservationLevel(brAPIobsUnitLevel);
                    brAPIobservationUnit.setObservationUnitPosition(brAPIobsUnitPos);

                    //Test (T) or Check (C) → ObservationUnit.observationUnitPosition.entryType
                    brAPIobsUnitPos.setEntryType(observationUnitData.getTest_or_check());
                    //Germplasm GID → ObservationUnit.germplasmDbId
                    //TODO This will require looking up the germplasm by external reference to get the correct DBID
                    brAPIobservationUnit.setGermplasmDbId(observationUnitData.getGid());

                    // Exp Unit → ObservationUnit.observationUnitPosition.observationLevel.levelName
                    brAPIobsUnitLevel.setLevelName(observationUnitData.getExp_unit());
                    // Exp Unit → Trial.additionalInfo.defaultObservationLevel
                    brAPITrial.putAdditionalInfoItem("defaultObservationLevel", observationUnitData.getExp_unit());

                    // Exp Unit Id → ObservationUnit.observationUnitName
                    brAPIobservationUnit.setObservationUnitName(observationUnitData.getExp_unit_id());
                }
            }

        }
    }


    @Override
    public void validateDependencies(Map<Integer, PendingImport> mappedBrAPIImport) throws ValidatorException {
        // TODO
    }

    @Override
    public void postBrapiData(Map<Integer, PendingImport> mappedBrAPIImport, Program program, ImportUpload upload) {

    }

    private void updateDependencyValues(Map<Integer, PendingImport> mappedBrAPIImport) {
        // TODO
    }
}