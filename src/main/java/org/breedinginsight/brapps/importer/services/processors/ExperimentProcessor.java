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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPILocation;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.*;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.daos.BrAPILocationDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIStudyDAO;
import org.breedinginsight.brapps.importer.daos.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
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

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    private DSLContext dsl;
    private BrAPITrialDAO brapiTrialDAO;
    private BrAPILocationDAO brAPILocationDAO;
    private BrAPIStudyDAO brAPIStudyDAO;
    private BrAPIObservationUnitDAO brAPIObservationUnitDAO;
    private final BrAPIGermplasmDAO brAPIGermplasmDAO;

    //These BrapiData-objects are initially populated by the getExistingBrapiData() method,
    // then updated by the getNewBrapiData() method.
    private Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope = null;
    private Map<String, PendingImportObject<BrAPILocation>> locationByName = null;
    private Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope = null;
    //  It is assumed that there are no pre-existing observation units for the given environment (so this will not be
    // intitalized by getExistingBrapiData
    private Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope = null;
    // existingGermplasmByGID is populated by getExistingBrapiData(), but not updated by the getNewBrapiData() method
    private Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = null;

    @Inject
    public ExperimentProcessor(DSLContext dsl,
                               BrAPITrialDAO brapiTrialDAO,
                               BrAPILocationDAO brAPILocationDAO,
                               BrAPIStudyDAO brAPIStudyDAO,
                               BrAPIObservationUnitDAO brAPIObservationUnitDAO,
                               BrAPIGermplasmDAO brAPIGermplasmDAO) {
        this.dsl = dsl;
        this.brapiTrialDAO = brapiTrialDAO;
        this.brAPILocationDAO = brAPILocationDAO;
        this.brAPIStudyDAO = brAPIStudyDAO;
        this.brAPIObservationUnitDAO = brAPIObservationUnitDAO;
        this.brAPIGermplasmDAO = brAPIGermplasmDAO;
    }

    /**
     * Initialize the Map<String, PendingImportObject> objects with existing BrAPI Data.
     * @param importRows
     * @param program
     */
    public void getExistingBrapiData(List<BrAPIImport> importRows, Program program) {

        List<ExperimentObservation> experimentImportRows = importRows.stream()
                .map(trialImport -> (ExperimentObservation) trialImport)
                .collect(Collectors.toList());

        this.trialByNameNoScope = initialize_trialByNameNoScope( program, experimentImportRows );
        this.locationByName = initialize_uniqueLocationNames ( program, experimentImportRows );
        this.studyByNameNoScope = initialize_studyByNameNoScope( program, experimentImportRows );
        this.observationUnitByNameNoScope = initialize_observationUnitByNameNoScope( program, experimentImportRows );
        this.existingGermplasmByGID = initialize_existingGermplasmByGID( program, experimentImportRows );
    }

    /**
     * @param importRows - one element of the list for every row of the import file.
     * @param mappedBrAPIImport - passed in by reference and modified within this program (this will latter be passed to the front end for the preview)
     * @param program
     * @param user
     * @param commit -  true when the data should be saved (ie when the user has pressed the "Commit" button)
     *                  false when used for preview only
     * @return Map<String, ImportPreviewStatistics> - used to display the summary statistics.
     * @throws ValidatorException
     */
    @Override
    public Map<String, ImportPreviewStatistics> process(
            List<BrAPIImport> importRows,
            Map<Integer, PendingImport> mappedBrAPIImport,
            Program program,
            User user,
            boolean commit) throws ValidatorException {

        ValidationErrors validationErrors = new ValidationErrors();

        // add "New" pending data to the BrapiData objects
        getNewBrapiData(importRows, program, commit);

        // For each import row
        for (int i = 0; i < importRows.size(); i++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(i);

            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(i, new PendingImport());
            mappedImportRow.setTrial( this.trialByNameNoScope.get( importRow.getExpTitle() ) );
            mappedImportRow.setLocation( this.locationByName.get( importRow.getEnvLocation() ) );
            mappedImportRow.setStudy( this.studyByNameNoScope.get( importRow.getEnv() ) );
            mappedImportRow.setObservationUnit( this.observationUnitByNameNoScope.get( importRow.getExpUnitId() ) );
            PendingImportObject<BrAPIGermplasm> germplasmPIO = getGidPio(importRow, mappedImportRow);
            mappedImportRow.setGermplasm( germplasmPIO );

            if (! StringUtils.isBlank( importRow.getGid() )) { // if GID is blank, don't bother to check if it is valid.
                validateGermplam(importRow,validationErrors, i, germplasmPIO);
            }
            // Construct Observations -- Done in another card
            mappedBrAPIImport.put(i, mappedImportRow);
        }
        // End-of-loop

        validationErrors = validateFields(importRows, validationErrors);

        if (validationErrors.hasErrors() ){
            throw new ValidatorException(validationErrors);
        }

        // Construct our response object
        return getStatisticsMap(importRows);
    }

    private void getNewBrapiData(List<BrAPIImport> importRows, Program program, boolean commit) {

        //TODO retrieve sequanceName from the program table.
        String studySequenceName = program.getStudySequence();
        if (studySequenceName == null) {
            log.error(String.format("Program, %s, is missing a value in the study sequence column.", program.getName()));
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Program is not properly configured for study import");
        }
        Supplier<BigInteger> studyNextVal = () -> dsl.nextval(studySequenceName.toLowerCase());

        String opsUnitSequenceName = program.getObsUnitSequence();
        if (opsUnitSequenceName == null) {
            log.error(String.format("Program, %s, is missing a value in the obsUnit sequence column.", program.getName()));
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Program is not properly configured for observation unit import");
        }
        Supplier<BigInteger> obsUnitNextVal = () -> dsl.nextval(opsUnitSequenceName.toLowerCase());

        for (int i = 0; i < importRows.size(); i++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(i);

            PendingImportObject<BrAPITrial> trialPIO = createTrialPIO(program, commit, importRow);
            this.trialByNameNoScope.put( importRow.getExpTitle(), trialPIO );

            PendingImportObject<BrAPILocation> locationPIO = createLocationPIO(importRow);
            this.locationByName.put( importRow.getEnvLocation(), locationPIO );

            PendingImportObject<BrAPIStudy> studyPIO = createStudyPIO(program, commit, studyNextVal, importRow);
            this.studyByNameNoScope.put( importRow.getEnv(),studyPIO );
            PendingImportObject<BrAPIObservationUnit> obsUnitPIO = createObsUnitPIO(program, commit, obsUnitNextVal, importRow);
            this.observationUnitByNameNoScope.put( importRow.getExpUnitId(), obsUnitPIO );
        }
    }

    private ValidationErrors validateFields(List<BrAPIImport> importRows, ValidationErrors validationErrors) {
        HashSet<String> uniqueStudyAndObsUnit = new HashSet<>();
        for (int i = 0; i < importRows.size(); i++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(i);
            validateConditionallyRequired(validationErrors, i, importRow);
            validateUniqueObsUnits(validationErrors, uniqueStudyAndObsUnit, i, importRow);
        }
        return validationErrors;
    }

    private void validateUniqueObsUnits(ValidationErrors validationErrors, HashSet<String> uniqueStudyAndObsUnit, int i, ExperimentObservation importRow) {
        String envIdPlusStudyId = importRow.getEnv() + importRow.getExpUnitId();
        if( uniqueStudyAndObsUnit.contains( envIdPlusStudyId )){
            String errorMessage = String.format("The ID (%s) is not unique within the environment(%s)", importRow.getExpUnitId(), importRow.getEnv());
            this.addRowError("Exp Unit ID", errorMessage, validationErrors, i);
        }
        else{
            uniqueStudyAndObsUnit.add( envIdPlusStudyId );
        }
    }

    private void validateConditionallyRequired(ValidationErrors validationErrors, int i, ExperimentObservation importRow) {
        String experimentTitle = importRow.getExpTitle();
        String obsUnitID = importRow.getObsUnitID();
        if( StringUtils.isBlank( obsUnitID )){
            validateRequiredCell(
                    experimentTitle,
                    "Exp Title",
                    "Field is blank", validationErrors, i
                    );
        }

        ImportObjectState expState = this.trialByNameNoScope.get(experimentTitle).getState();
        boolean isExperimentNew = (expState == ImportObjectState.NEW);

        if (isExperimentNew) {
            String errorMessage = "Field is blank when creating a new experiment";
            validateRequiredCell( importRow.getGid(),
                    "GID",
                    errorMessage, validationErrors, i);
            validateRequiredCell( importRow.getExpUnit(),
                    "Exp Unit",
                    errorMessage, validationErrors, i);
            validateRequiredCell( importRow.getExpType(),
                    "Exp Type",
                    errorMessage, validationErrors, i);
            validateRequiredCell( importRow.getEnv(),
                    "Env",
                    errorMessage, validationErrors, i);
            validateRequiredCell( importRow.getEnvLocation(),
                    "Env Location",
                    errorMessage, validationErrors, i);
            validateRequiredCell( importRow.getEnvYear(),
                    "Env Year",
                    errorMessage, validationErrors, i);
            validateRequiredCell( importRow.getExpUnitId(),
                    "Exp Unit ID",
                    errorMessage, validationErrors, i);
            validateRequiredCell( importRow.getExpReplicateNo(),
                    "Exp Replicate #",
                    errorMessage, validationErrors, i);
            validateRequiredCell( importRow.getExpBlockNo(),
                    "Exp Block #",
                    errorMessage, validationErrors, i);
        }
    }

    private void validateRequiredCell(String value, String columHeader, String errorMessage, ValidationErrors validationErrors, int i) {
        if ( StringUtils.isBlank( value )) {
            addRowError(
                    columHeader,
                    errorMessage,
                    validationErrors, i
            ) ;
        }
    }

    private void addRowError(String field, String errorMessage, ValidationErrors validationErrors, int i) {
        ValidationError ve = new ValidationError(field, errorMessage, HttpStatus.UNPROCESSABLE_ENTITY);
        validationErrors.addError(i + 2, ve);  // +2 instead of +1 to account for the column header row.
    }

    private void addIfNotNull(HashSet<String> set, String setValue) {
        if( setValue!=null) { set.add(setValue); }
    }

    private Map<String, ImportPreviewStatistics> getStatisticsMap(List<BrAPIImport> importRows) {
        // Data for stats.
        HashSet<String> environmentNameCounter = new HashSet<>(); // set of unique environment names
        HashSet<String> obsUnitsIDCounter = new HashSet<>(); // set of unique observation unit ID's
        HashSet<String> gidCounter = new HashSet<>(); // set of unique GID's
        for (int i = 0; i < importRows.size(); i++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(i);
            // Collect date for stats.
            addIfNotNull(environmentNameCounter, importRow.getEnv());
            addIfNotNull(obsUnitsIDCounter, importRow.getExpUnitId());
            addIfNotNull(gidCounter, importRow.getGid());
        }
        ImportPreviewStatistics environmentStats = ImportPreviewStatistics.builder()
                .newObjectCount(environmentNameCounter.size())
                .build();
        ImportPreviewStatistics obdUnitStats = ImportPreviewStatistics.builder()
                .newObjectCount(obsUnitsIDCounter.size())
                .build();
        ImportPreviewStatistics gidStats = ImportPreviewStatistics.builder()
                .newObjectCount(gidCounter.size())
                .build();

        return Map.of(
                "Environments",         environmentStats,
                "Observation_Units",    obdUnitStats,
                "GIDs",                 gidStats
        );
    }

    private void validateGermplam(ExperimentObservation importRow, ValidationErrors validationErrors, int i, PendingImportObject<BrAPIGermplasm> germplasmPIO) {
       // error if GID is not blank but GID dose not already exist
        if( !StringUtils.isBlank( importRow.getGid()) && germplasmPIO == null ) {
            addRowError(
                    "GID",
                    "A non-existing GID",
                    validationErrors, i
            );
        }
    }

    private PendingImportObject<BrAPIGermplasm> getGidPio(ExperimentObservation importRow, PendingImport mappedImportRow) {
        if( this.existingGermplasmByGID.containsKey( importRow.getGid() )){
            return existingGermplasmByGID.get(importRow.getGid());
        }
        else{
            return null;
        }
    }

    private PendingImportObject<BrAPIObservationUnit> createObsUnitPIO(Program program, boolean commit, Supplier<BigInteger> obsUnitNextVal, ExperimentObservation importRow) {
        PendingImportObject<BrAPIObservationUnit> pio = null;
        if( this.observationUnitByNameNoScope.containsKey( importRow.getExpUnitId() ) ) {
            pio = observationUnitByNameNoScope.get(importRow.getExpUnitId() ) ;
        }
        else{
            BrAPIObservationUnit newObservationUnit = importRow.constructBrAPIObservationUnit(program, obsUnitNextVal, commit);
            pio = new PendingImportObject<>(ImportObjectState.NEW, newObservationUnit);
        }
        return pio;
    }

    private PendingImportObject<BrAPIStudy> createStudyPIO(Program program, boolean commit, Supplier<BigInteger> studyNextVal, ExperimentObservation importRow ) {
        PendingImportObject<BrAPIStudy> pio = null;
        if( studyByNameNoScope.containsKey( importRow.getEnv()) ) {
            pio =  studyByNameNoScope.get( importRow.getEnv() ) ;
        }
        else{
            BrAPIStudy newStudy = importRow.constructBrAPIStudy(program, studyNextVal, commit);
            pio = new PendingImportObject<>(ImportObjectState.NEW, newStudy);
        }
        return pio;
    }

    private PendingImportObject<BrAPILocation> createLocationPIO(ExperimentObservation importRow) {
        PendingImportObject<BrAPILocation> pio = null;
        if( locationByName.containsKey(( importRow.getEnvLocation() ))){
            pio =  locationByName.get( importRow.getEnvLocation() );
        }
        else{
            BrAPILocation newLocation = importRow.constructBrAPILocation();
            pio = new PendingImportObject<>(ImportObjectState.NEW, newLocation);
        }
        return pio;
    }

    private PendingImportObject<BrAPITrial> createTrialPIO(Program program, boolean commit, ExperimentObservation importRow) {
        PendingImportObject<BrAPITrial> pio = null;
        if( trialByNameNoScope.containsKey( importRow.getExpTitle()) ) {
            pio = trialByNameNoScope.get( importRow.getExpTitle() ) ;
       }
       else {
            BrAPITrial newTrial = importRow.constructBrAPITrial(program, commit);
            pio = new PendingImportObject<>(ImportObjectState.NEW, newTrial);
        }
        return pio;
    }

    @Override
    public void validateDependencies(Map<Integer, PendingImport> mappedBrAPIImport) throws ValidatorException {
        // TODO
    }

    @Override
    public void postBrapiData(Map<Integer, PendingImport> mappedBrAPIImport, Program program, ImportUpload upload) {

        List<BrAPITrial> trials = ProcessorData.getNewObjects(this.trialByNameNoScope);
        List<BrAPILocation> locations = ProcessorData.getNewObjects(this.locationByName);
        List<BrAPIStudy> studies = ProcessorData.getNewObjects(this.studyByNameNoScope);
        List<BrAPIObservationUnit> observationUnits = ProcessorData.getNewObjects(this.observationUnitByNameNoScope);

        List<BrAPITrial> createdTrials = new ArrayList<>();
        List<BrAPILocation> createdLocations = new ArrayList<>();
        List<BrAPIStudy> createdStudies = new ArrayList<>();
        List<BrAPIObservationUnit> createdObservationUnits = new ArrayList<>();
        try {
            createdTrials.addAll(brapiTrialDAO.createBrAPITrial(trials, program.getId(), upload));
            for ( BrAPITrial createdTrial: createdTrials ) {
                String createdTrialName = createdTrial.getTrialName();
                String createdTrialName_no_key = Utilities.removeProgramKey( createdTrialName, program.getKey() );
                PendingImportObject<BrAPITrial> pi = this.trialByNameNoScope.get(createdTrialName_no_key);
                BrAPITrial listedTrial = pi.getBrAPIObject();
                String dbid = createdTrial.getTrialDbId();
                listedTrial.setTrialDbId( dbid );
            }
            createdLocations.addAll(brAPILocationDAO.createBrAPILocation(locations, program.getId(), upload));

            updateStudyDependencyValues(mappedBrAPIImport,program.getKey());
            createdStudies.addAll(brAPIStudyDAO.createBrAPIStudy(studies, program.getId(), upload));
            for( BrAPIStudy createdStudy : createdStudies){
                String createdStudy_name_no_key = Utilities.removeProgramKeyAndUnknownAdditionalData( createdStudy.getStudyName(), program.getKey() );
                PendingImportObject<BrAPIStudy> pi = this.studyByNameNoScope.get( createdStudy_name_no_key );
                BrAPIStudy brAPIStudy = pi.getBrAPIObject();
                brAPIStudy.setStudyDbId( createdStudy.getStudyDbId() );
            }

            updateObsUnitDependencyValues(mappedBrAPIImport,program.getKey());
        } catch (ApiException e) {
            throw new InternalServerException(e.toString(), e);
        }

//        // Update our records
//        createdTrials.forEach(trial -> {
//            PendingImportObject<BrAPITrial> preview = this.trialByNameNoScope.get( Utilities.removeProgramKey( trial.getTrialName(),program.getKey() ) );
//            preview.setBrAPIObject(trial);
//        });
//        createdLocations.forEach(location -> {
//            PendingImportObject<BrAPILocation> preview = locationByName.get(location.getLocationName());
//            preview.setBrAPIObject(location);
//        });
//        createdStudies.forEach(study -> {
//            String studyName = Utilities.removeProgramKey(study.getStudyName(),program.getKey() );
//            PendingImportObject<BrAPIStudy> preview = this.studyByNameNoScope.get( studyName ) ;
//            preview.setBrAPIObject(study);
//        });
    }

//    private void updateDependencyValues(Map<Integer, PendingImport> mappedBrAPIImport) {
//        updateStudyDependencyValues(mappedBrAPIImport);
//    }

    private void updateObsUnitDependencyValues(Map<Integer, PendingImport> mappedBrAPIImport, String programKey) {

        // update study DbIds
        this.studyByNameNoScope.values().stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(study -> updateStudyDbId(study, programKey));

        // update germplasm DbIds
        this.existingGermplasmByGID.values().stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(this::updateGermplasmDbId);
    }

    private void updateStudyDbId(BrAPIStudy study, String programKey) {
        this.observationUnitByNameNoScope.values().stream()
                .filter(obsUnit -> obsUnit.getBrAPIObject().getStudyName().equals( Utilities.removeProgramKeyAndUnknownAdditionalData( study.getStudyName(), programKey ) ))
                .forEach(obsUnit -> obsUnit.getBrAPIObject().setStudyDbId(study.getStudyDbId()));
    }

//    private void updateGermplasmDbId(BrAPIGermplasm germplasm) {
//        this.observationUnitByNameNoScope.values().stream()
//                .filter(obsUnit -> obsUnit.getBrAPIObject().getGermplasmName() != null &&
//                        obsUnit.getBrAPIObject().getGermplasmName().equals(germplasm.getGermplasmName()))
//                .forEach(obsUnit -> obsUnit.getBrAPIObject().setGermplasmDbId(germplasm.getGermplasmDbId()));
//    }

    private void updateGermplasmDbId(BrAPIGermplasm germplasm) {
        this.observationUnitByNameNoScope.values().stream()
                .filter(obsUnit -> obsUnit.getBrAPIObject().getGermplasmName() != null &&
                        obsUnit.getBrAPIObject().getGermplasmName().equals(germplasm.getGermplasmName()))
                .forEach(obsUnit -> obsUnit.getBrAPIObject().setGermplasmDbId(germplasm.getGermplasmDbId()));
    }


    private void updateStudyDependencyValues(Map<Integer, PendingImport> mappedBrAPIImport, String programKey) {
        // update location DbIds in studies for all distinct locations
        mappedBrAPIImport.values().stream()
                .map(PendingImport::getLocation)
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(this::updateStudyLocationDbId);

        // update trial DbIds in studies for all distinct trials
        this.trialByNameNoScope.values().stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(trait -> this.updateTrialDbId(trait, programKey));
    }

    private void updateStudyLocationDbId(BrAPILocation location) {
        this.studyByNameNoScope.values().stream()
                .filter(study -> study.getBrAPIObject().getLocationName().equals(location.getLocationName()))
                .forEach(study -> study.getBrAPIObject().setLocationDbId(location.getLocationDbId()));
    }

    private void updateTrialDbId(BrAPITrial trial, String programKey) {
        this.studyByNameNoScope.values().stream()
                .filter(study -> study.getBrAPIObject().getTrialName().equals(Utilities.removeProgramKey(trial.getTrialName(), programKey ) ) )
                .forEach(study -> study.getBrAPIObject().setTrialDbId(trial.getTrialDbId()));
    }

    @Override
    public String getName() {
        return NAME;
    }

    //TODO should this method be moved to BrAPIExperimentService.java
    private ArrayList<BrAPIGermplasm> getGermplasmByAccessionNumber(
            List<String> germplasmAccessionNumbers,
            UUID programId) throws ApiException {
        List<BrAPIGermplasm> germplasmList = brAPIGermplasmDAO.getGermplasm(programId);
        ArrayList<BrAPIGermplasm> resultGermplasm = new ArrayList<>();
        // Search for accession number matches
        for (BrAPIGermplasm germplasm: germplasmList) {
            for (String accessionNumber: germplasmAccessionNumbers) {
                if (germplasm.getAccessionNumber().equals(accessionNumber)) {
                    resultGermplasm.add(germplasm);
                    break;
                }
            }
        }
        return resultGermplasm;
    }

    private Map<String, PendingImportObject<BrAPIGermplasm>> initialize_existingGermplasmByGID(Program program, List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = new HashMap<>();
        List<String> uniqueGermplasmGID = experimentImportRows.stream()
                .map(experimentImport -> experimentImport.getGid())
                .distinct()
                .collect(Collectors.toList());

        List<BrAPIGermplasm> existingGermplasms;
        try {
            existingGermplasms = this.getGermplasmByAccessionNumber(uniqueGermplasmGID, program.getId());
            existingGermplasms.forEach(existingGermplasm -> {
                existingGermplasmByGID.put(existingGermplasm.getAccessionNumber(), new PendingImportObject<>(ImportObjectState.EXISTING, existingGermplasm));
            });
            return existingGermplasmByGID;
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }
    }
    private Map<String, PendingImportObject<BrAPIObservationUnit>> initialize_observationUnitByNameNoScope(Program program, List<ExperimentObservation> experimentImportRows) {

        Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope = new HashMap<>();
        List<String> uniqueObservationUnitNames = experimentImportRows.stream()
                .map(experimentImport -> experimentImport.getExpUnitId())
                .distinct()
                .filter(name -> null !=name)
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
            return observationUnitByNameNoScope;
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }
    }

    private Map<String, PendingImportObject<BrAPIStudy>> initialize_studyByNameNoScope(Program program, List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope = new HashMap<>();
        List<String> uniqueStudyNames = experimentImportRows.stream()
                .map(experimentImport -> experimentImport.getEnv())
                .distinct()
                .filter(name -> null !=name)
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
            return studyByNameNoScope;
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }
    }

    private Map<String, PendingImportObject<BrAPILocation>> initialize_uniqueLocationNames(Program program, List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPILocation>> locationByName = new HashMap<>();
        List<String> uniqueLocationNames = experimentImportRows.stream()
                .map(experimentImport -> experimentImport.getEnvLocation())
                .distinct()
                .filter(name -> null !=name)
                .collect(Collectors.toList());

        List<BrAPILocation> existingLocations;
        try {
            existingLocations = brAPILocationDAO.getLocationsByName(uniqueLocationNames, program.getId());
            existingLocations.forEach(existingLocation -> {
                locationByName.put(existingLocation.getLocationName(), new PendingImportObject<>(ImportObjectState.EXISTING, existingLocation));
            });
            return locationByName;
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }
    }

    private Map<String, PendingImportObject<BrAPITrial>> initialize_trialByNameNoScope(Program program, List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope = new HashMap<>();
        List<String> uniqueTrialNames = experimentImportRows.stream()
                .map(experimentImport -> experimentImport.getExpTitle())
                .distinct().
                filter(name -> null !=name)
                .collect(Collectors.toList());
        List<BrAPITrial> existingTrials;
log.info(uniqueTrialNames.toString());

        try {
            existingTrials = brapiTrialDAO.getTrialByName(uniqueTrialNames, program);
            existingTrials.forEach(existingTrial -> {
                trialByNameNoScope.put(
                        Utilities.removeProgramKey(existingTrial.getTrialName(), program.getKey()),
                        new PendingImportObject<>(ImportObjectState.EXISTING, existingTrial));
            });
            return trialByNameNoScope;
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }
    }
    private String simpleStudyName(String scopedName){
        return scopedName.replaceFirst(" \\[.*\\]", "");
    }


}