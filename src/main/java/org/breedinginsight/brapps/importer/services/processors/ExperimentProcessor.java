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
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPILocation;
import org.brapi.v2.model.core.BrAPISeason;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.*;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.daos.*;
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
    private BrAPISeasonDAO brAPISeasonDAO;
    private BrAPIGermplasmDAO brAPIGermplasmDAO;

    // used to make the yearsToSeasonDbId() function more efficient
    private Map<String, String > yearToSeasonDbIdCache = new HashMap<>();

    //These BrapiData-objects are initially populated by the getExistingBrapiData() method,
    // then updated by the getNewBrapiData() method.
    private Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope = null;
    private Map<String, PendingImportObject<BrAPILocation>> locationByName = null;
    private Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope = null;
    //  It is assumed that there are no preexisting Observation Units for the given environment (so this will not be
    // initialized by getExistingBrapiData() )
    private Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope = null;
    // existingGermplasmByGID is populated by getExistingBrapiData(), but not updated by the getNewBrapiData() method
    private Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = null;

    @Inject
    public ExperimentProcessor(DSLContext dsl,
                               BrAPITrialDAO brapiTrialDAO,
                               BrAPILocationDAO brAPILocationDAO,
                               BrAPIStudyDAO brAPIStudyDAO,
                               BrAPIObservationUnitDAO brAPIObservationUnitDAO,
                               BrAPISeasonDAO brAPISeasonDAO,
                               BrAPIGermplasmDAO brAPIGermplasmDAO) {
        this.dsl = dsl;
        this.brapiTrialDAO = brapiTrialDAO;
        this.brAPILocationDAO = brAPILocationDAO;
        this.brAPIStudyDAO = brAPIStudyDAO;
        this.brAPIObservationUnitDAO = brAPIObservationUnitDAO;
        this.brAPISeasonDAO = brAPISeasonDAO;
        this.brAPIGermplasmDAO = brAPIGermplasmDAO;
    }

    /**
     * Initialize the Map<String, PendingImportObject> objects with existing BrAPI Data.
     * @param importRows
     * @param program
     */
    @Override
    public void getExistingBrapiData(List<BrAPIImport> importRows, Program program) {

        List<ExperimentObservation> experimentImportRows = importRows.stream()
                .map(trialImport -> (ExperimentObservation) trialImport)
                .collect(Collectors.toList());

        this.trialByNameNoScope = initialize_trialByNameNoScope( program, experimentImportRows );
        this.locationByName = initialize_uniqueLocationNames( program, experimentImportRows );
        this.studyByNameNoScope = initialize_studyByNameNoScope( program, experimentImportRows );
        // All of the Observation Units will be new.  None will be preexisting.
        this.observationUnitByNameNoScope =  new HashMap<>();
        this.existingGermplasmByGID = initialize_existingGermplasmByGID( program, experimentImportRows );
    }

    /**
     * @param importRows - one element of the list for every row of the import file.
     * @param mappedBrAPIImport - passed in by reference and modified within this program (this will later be passed to the front end for the preview)
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
            mappedImportRow.setObservationUnit( this.observationUnitByNameNoScope.get( createObservationUnitKey( importRow ) ) );
            PendingImportObject<BrAPIGermplasm> germplasmPIO = getGidPOI(importRow);
            mappedImportRow.setGermplasm( germplasmPIO );

            if (! StringUtils.isBlank( importRow.getGid() )) { // if GID is blank, don't bother to check if it is valid.
                validateGermplasm(importRow,validationErrors, i, germplasmPIO);
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

        String obsUnitSequenceName = program.getObsUnitSequence();
        if (obsUnitSequenceName == null) {
            log.error(String.format("Program, %s, is missing a value in the obsUnit sequence column.", program.getName()));
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Program is not properly configured for observation unit import");
        }
        Supplier<BigInteger> obsUnitNextVal = () -> dsl.nextval(obsUnitSequenceName.toLowerCase());

        // It is assumed, at this point, that there is only one experiment per import-file
        String expSeqValue = null;
        if (commit) {
            String expUnitSequenceName = program.getExpSequence();
            if (expUnitSequenceName == null) {
                log.error(String.format("Program, %s, is missing a value in the exp sequence column.", program.getName()));
                throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Program is not properly configured for experiment import");
            }
            expSeqValue = dsl.nextval(expUnitSequenceName.toLowerCase()).toString();
        }

        for (BrAPIImport row : importRows) {
            ExperimentObservation importRow = (ExperimentObservation) row;

            PendingImportObject<BrAPITrial> trialPIO = createTrialPIO(program, commit, importRow, expSeqValue);
            this.trialByNameNoScope.put(importRow.getExpTitle(), trialPIO);

            //TODO move this above the creation of trialPIO (see BI-1189 tech spec 4.b)?
            PendingImportObject<BrAPILocation> locationPIO = createLocationPIO(importRow);
            this.locationByName.put(importRow.getEnvLocation(), locationPIO);

            PendingImportObject<BrAPIStudy> studyPIO = createStudyPIO(program, commit, expSeqValue, importRow);
            this.studyByNameNoScope.put(importRow.getEnv(), studyPIO);

            PendingImportObject<BrAPIObservationUnit> obsUnitPIO = createObsUnitPIO(program, commit, obsUnitNextVal, importRow);
            String key = createObservationUnitKey(importRow);
            this.observationUnitByNameNoScope.put(key, obsUnitPIO);
        }
    }

    private String createObservationUnitKey(ExperimentObservation importRow) {
        String key = importRow.getEnv() + importRow.getExpUnitId();
        return key;
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

    /**
     * Validate that the the observation unit is unique within a study.
     * <br>
     * SIDE EFFECTS:  validationErrors and uniqueStudyAndObsUnit can be modified.
     * @param validationErrors can be modified as a side effect.
     * @param uniqueStudyAndObsUnit can be modified as a side effect.
     * @param i counter that is always two less the file row being validated
     * @param importRow the data row being validated
     */
    private void validateUniqueObsUnits(
            ValidationErrors validationErrors,
            HashSet<String> uniqueStudyAndObsUnit,
            int i,
            ExperimentObservation importRow) {
        String envIdPlusStudyId = createObservationUnitKey( importRow );
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

    private void validateRequiredCell(String value, String columnHeader, String errorMessage, ValidationErrors validationErrors, int i) {
        if ( StringUtils.isBlank( value )) {
            addRowError(
                    columnHeader,
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
        for (BrAPIImport row : importRows) {
            ExperimentObservation importRow = (ExperimentObservation) row;
            // Collect date for stats.
            addIfNotNull(environmentNameCounter, importRow.getEnv());
            addIfNotNull(obsUnitsIDCounter, createObservationUnitKey( importRow ));
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

    private void validateGermplasm(ExperimentObservation importRow, ValidationErrors validationErrors, int i, PendingImportObject<BrAPIGermplasm> germplasmPIO) {
       // error if GID is not blank but GID does not already exist
        if( !StringUtils.isBlank( importRow.getGid()) && germplasmPIO == null ) {
            addRowError(
                    "GID",
                    "A non-existing GID",
                    validationErrors, i
            );
        }
    }

    private PendingImportObject<BrAPIGermplasm> getGidPOI(ExperimentObservation importRow) {
        if( this.existingGermplasmByGID.containsKey( importRow.getGid() )){
            return existingGermplasmByGID.get(importRow.getGid());
        }
        else{
            return null;
        }
    }

    private PendingImportObject<BrAPIObservationUnit> createObsUnitPIO(Program program, boolean commit, Supplier<BigInteger> obsUnitNextVal, ExperimentObservation importRow) {
        PendingImportObject<BrAPIObservationUnit> pio = null;
        if( this.observationUnitByNameNoScope.containsKey( createObservationUnitKey( importRow ) ) ) {
            pio = observationUnitByNameNoScope.get( createObservationUnitKey( importRow ) ) ;
        }
        else{
            String germplasmName = "";
            if( this.existingGermplasmByGID.get( importRow.getGid() ) != null) {
                germplasmName = this.existingGermplasmByGID.get(importRow.getGid()).getBrAPIObject().getGermplasmName();
            }
            PendingImportObject<BrAPITrial> trialPIO = this.trialByNameNoScope.get(importRow.getExpTitle());
            UUID trialID = trialPIO.getId();
            PendingImportObject<BrAPIStudy> studyPIO = this.studyByNameNoScope.get(importRow.getEnv());
            UUID studyID = studyPIO.getId();
            UUID id = UUID.randomUUID();
            BrAPIObservationUnit newObservationUnit = importRow.constructBrAPIObservationUnit(program, obsUnitNextVal, commit, germplasmName, BRAPI_REFERENCE_SOURCE, trialID, studyID, id);
            pio = new PendingImportObject<>(ImportObjectState.NEW, newObservationUnit);
        }
        return pio;
    }

    private PendingImportObject<BrAPIStudy> createStudyPIO(Program program, boolean commit, String expSeqenceValue, ExperimentObservation importRow) {
        PendingImportObject<BrAPIStudy> pio = null;
        if( studyByNameNoScope.containsKey( importRow.getEnv()) ) {
            pio =  studyByNameNoScope.get( importRow.getEnv() ) ;
        }
        else{
            PendingImportObject<BrAPITrial> trialPIO = this.trialByNameNoScope.get(importRow.getExpTitle());
            UUID trialID = trialPIO.getId();
            UUID id = UUID.randomUUID();
            BrAPIStudy newStudy = importRow.constructBrAPIStudy(program, commit, BRAPI_REFERENCE_SOURCE, expSeqenceValue, trialID, id);

            if( commit) {
                String seasonID = this.yearsToSeasonDbId(newStudy.getSeasons(), program.getId());
                newStudy.setSeasons(Arrays.asList(seasonID));
            }

            pio = new PendingImportObject<>(ImportObjectState.NEW, newStudy, id);
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

    private PendingImportObject<BrAPITrial> createTrialPIO(Program program, boolean commit, ExperimentObservation importRow, String expSeqValue) {
        PendingImportObject<BrAPITrial> pio = null;
        if( trialByNameNoScope.containsKey( importRow.getExpTitle()) ) {
            pio = trialByNameNoScope.get( importRow.getExpTitle() ) ;
       }
       else {
            UUID id = UUID.randomUUID();
            BrAPITrial newTrial = importRow.constructBrAPITrial(program, commit, BRAPI_REFERENCE_SOURCE, id, expSeqValue);
            pio = new PendingImportObject<>(ImportObjectState.NEW, newTrial, id);
        }
        return pio;
    }

    @Override
    public void validateDependencies(Map<Integer, PendingImport> mappedBrAPIImport) throws ValidatorException {
        // TODO
    }

    @Override
    public void postBrapiData(Map<Integer, PendingImport> mappedBrAPIImport, Program program, ImportUpload upload) {

        List<BrAPITrial> newTrials = ProcessorData.getNewObjects(this.trialByNameNoScope);
        List<BrAPILocation> newLocations = ProcessorData.getNewObjects(this.locationByName);
        List<BrAPIStudy> newStudies = ProcessorData.getNewObjects(this.studyByNameNoScope);
        List<BrAPIObservationUnit> newObservationUnits = ProcessorData.getNewObjects(this.observationUnitByNameNoScope);

        try {
            List<BrAPITrial> createdTrials = new ArrayList<>(brapiTrialDAO.createBrAPITrial(newTrials, program.getId(), upload));

            // set the DbId to the for each newly created trial
            for ( BrAPITrial createdTrial: createdTrials ) {
                String createdTrialName = createdTrial.getTrialName();
                String createdTrialName_no_key = Utilities.removeProgramKey( createdTrialName, program.getKey() );
                PendingImportObject<BrAPITrial> pi = this.trialByNameNoScope.get(createdTrialName_no_key);
                BrAPITrial listedTrial = pi.getBrAPIObject();
                String dbid = createdTrial.getTrialDbId();
                listedTrial.setTrialDbId( dbid );
            }
            brAPILocationDAO.createBrAPILocation(newLocations, program.getId(), upload);

            updateStudyDependencyValues(mappedBrAPIImport,program.getKey());
            List<BrAPIStudy> createdStudies = new ArrayList<>();
            createdStudies.addAll( brAPIStudyDAO.createBrAPIStudy(newStudies, program.getId(), upload) );

            // set the DbId to the for each newly created study
            for( BrAPIStudy createdStudy : createdStudies){
                String createdStudy_name_no_key = Utilities.removeProgramKeyAndUnknownAdditionalData( createdStudy.getStudyName(), program.getKey() );
                PendingImportObject<BrAPIStudy> pi = this.studyByNameNoScope.get( createdStudy_name_no_key );
                BrAPIStudy brAPIStudy = pi.getBrAPIObject();
                brAPIStudy.setStudyDbId( createdStudy.getStudyDbId() );
            }

            updateObsUnitDependencyValues(program.getKey());
            brAPIObservationUnitDAO.createBrAPIObservationUnits(newObservationUnits, program.getId(), upload);
        } catch (ApiException e) {
            log.error(e.getResponseBody());
            throw new InternalServerException(e.toString(), e);
        }

    }

    private void updateObsUnitDependencyValues(String programKey) {

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
        List<String> uniqueGermplasmGIDs = experimentImportRows.stream()
                .map(ExperimentObservation::getGid)
                .distinct()
                .collect(Collectors.toList());

        List<BrAPIGermplasm> existingGermplasms;
        try {
            existingGermplasms = this.getGermplasmByAccessionNumber(uniqueGermplasmGIDs, program.getId());
            existingGermplasms.forEach(existingGermplasm -> existingGermplasmByGID.put(existingGermplasm.getAccessionNumber(), new PendingImportObject<>(ImportObjectState.EXISTING, existingGermplasm)));
            return existingGermplasmByGID;
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }
    }

    private Map<String, PendingImportObject<BrAPIStudy>> initialize_studyByNameNoScope(Program program, List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope = new HashMap<>();


        if( this.trialByNameNoScope.size()!=1){
            return studyByNameNoScope;
        }
        ExperimentObservation experimentObservation = experimentImportRows.get(0);

        PendingImportObject<BrAPITrial> trial = this.trialByNameNoScope.get(experimentObservation.getExpTitle());
        List<BrAPIExternalReference> experimentRefs = trial.getBrAPIObject().getExternalReferences();
        Optional <BrAPIExternalReference> experimentIDRef = experimentRefs.stream()
                .filter(this::isRefSource)
                .findFirst();
        if( experimentIDRef.isEmpty()){
            throw new InternalServerException("An Experiment ID was not found any of the external references");
        }

        //get experimentID
        String experimentIDStr = experimentIDRef.get().getReferenceID();

        List<BrAPIStudy> existingStudies;
        try {
            existingStudies = brAPIStudyDAO.getStudiesByExperimentID(UUID.fromString(experimentIDStr), program);
            existingStudies.forEach(existingStudy -> {

                existingStudy.setStudyName(Utilities.removeProgramKeyAndUnknownAdditionalData(existingStudy.getStudyName(), program.getKey()));
                studyByNameNoScope.put(
                        existingStudy.getStudyName(),
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
                .map(ExperimentObservation::getEnvLocation)
                .distinct()
                .filter(Objects::nonNull)
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
        String programKey = program.getKey();
        List<String> uniqueTrialNames = experimentImportRows.stream()
                .map(experimentImport -> Utilities.appendProgramKey( experimentImport.getExpTitle(), programKey, null) )
                .distinct()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<BrAPITrial> existingTrials;

        try {
            existingTrials = brapiTrialDAO.getTrialByName(uniqueTrialNames, program);
            existingTrials.forEach(existingTrial -> {
                existingTrial.setTrialName(Utilities.removeProgramKey(existingTrial.getTrialName(), program.getKey()));
                trialByNameNoScope.put(
                        existingTrial.getTrialName(),
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

    /**
     * Converts year String to SeasonDbId
     * <br>
     * NOTE: This assumes that the only Season records of interest are ones
     * with a blank name or a name that is the same as the year.
     *
     * @param years this only looks at the first year of the list.
     * @param programId the program ID.
     * @return the DbId of the season-record associated with the first year
     * of the 'years' list (see NOTE above)
     */
    private String yearsToSeasonDbId(List<String> years, UUID programId) {
        String year = years.get(0);
        String dbID = null;
        if (this.yearToSeasonDbIdCache.containsKey(year) ){ // get it from cache if possable
            dbID = this.yearToSeasonDbIdCache.get(year);
        }
        else{
            dbID = this.yearToSeasonDbIdFromDatabase(year,programId);
            this.yearToSeasonDbIdCache.put(year, dbID);
        }
        return dbID;
    }

    private String yearToSeasonDbIdFromDatabase(String year, UUID programId) {
        BrAPISeason targetSeason = null;
        List<BrAPISeason> seasons;
        try {
            seasons = this.brAPISeasonDAO.getSeasonByYear(year, programId);
            for( BrAPISeason season : seasons){
                if(null == season.getSeasonName() || season.getSeasonName().isBlank() || season.getSeasonName().equals(year)){
                    targetSeason = season;
                    break;
                }
            }
            if (targetSeason == null){
                BrAPISeason newSeason = new BrAPISeason();
                newSeason.setYear( Integer.parseInt(year) );
                newSeason.setSeasonName( year );
                targetSeason = this.brAPISeasonDAO.addOneSeason( newSeason, programId );
            }

        } catch (ApiException e) {
            log.error(e.getResponseBody(), e);;
        }

        String seasonDbId = (targetSeason==null) ? null : targetSeason.getSeasonDbId();
        return seasonDbId;
    }

    private boolean isRefSource(BrAPIExternalReference brAPIExternalReference) {
        return brAPIExternalReference.getReferenceSource().equals(BRAPI_REFERENCE_SOURCE);
    }
}