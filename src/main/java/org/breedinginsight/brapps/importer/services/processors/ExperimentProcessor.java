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
import org.apache.commons.codec.digest.DigestUtils;
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
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.daos.*;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.FileMappingUtil;
import org.breedinginsight.dao.db.tables.pojos.TraitEntity;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Scale;
import org.breedinginsight.model.Trait;
import org.breedinginsight.model.User;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.utilities.Utilities;
import org.jooq.DSLContext;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.breedinginsight.brapps.importer.services.FileMappingUtil.EXPERIMENT_TEMPLATE_NAME;

@Slf4j
@Prototype
public class ExperimentProcessor implements Processor {

    private static final String NAME = "Experiment";
    private static final String MISSING_OBS_UNIT_ID_ERROR = "Experiment Units are missing Observation Unit Id.\n" +
            "If you’re trying to add these units to the experiment, please create a new environment" +
            " with all appropriate experiment units (NOTE: this will generate new Observation Unit Ids " +
            "for each experiment unit).";

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    private DSLContext dsl;
    private BrAPITrialDAO brapiTrialDAO;
    private BrAPILocationDAO brAPILocationDAO;
    private BrAPIStudyDAO brAPIStudyDAO;
    private BrAPIObservationUnitDAO brAPIObservationUnitDAO;
    private BrAPIObservationDAO brAPIObservationDAO;
    private BrAPISeasonDAO brAPISeasonDAO;
    private BrAPIGermplasmDAO brAPIGermplasmDAO;
    private OntologyService ontologyService;
    private FileMappingUtil fileMappingUtil;

    // used to make the yearsToSeasonDbId() function more efficient
    private final Map<String, String > yearToSeasonDbIdCache = new HashMap<>();
    // used to make the seasonDbIdtoYear() function more efficient
    private final Map<String, String > seasonDbIdToYearCache = new HashMap<>();

    //These BrapiData-objects are initially populated by the getExistingBrapiData() method,
    // then updated by the getNewBrapiData() method.
    private Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope = null;
    private Map<String, PendingImportObject<BrAPILocation>> locationByName = null;
    private Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope = null;
    //  It is assumed that there are no preexisting Observation Units for the given environment (so this will not be
    // initialized by getExistingBrapiData() )
    private Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope = null;

    private Map<String, PendingImportObject<BrAPIObservation>> observationByHash = new HashMap<>();

    // existingGermplasmByGID is populated by getExistingBrapiData(), but not updated by the getNewBrapiData() method
    private Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = null;

    // Associates timestamp columns to associated phenotype column name for ease of storage
    private Map<String, Column> timeStampColByPheno = new HashMap<>();

    @Inject
    public ExperimentProcessor(DSLContext dsl,
                               BrAPITrialDAO brapiTrialDAO,
                               BrAPILocationDAO brAPILocationDAO,
                               BrAPIStudyDAO brAPIStudyDAO,
                               BrAPIObservationUnitDAO brAPIObservationUnitDAO,
                               BrAPIObservationDAO brAPIObservationDAO,
                               BrAPISeasonDAO brAPISeasonDAO,
                               BrAPIGermplasmDAO brAPIGermplasmDAO,
                               OntologyService ontologyService,
                               FileMappingUtil fileMappingUtil) {
        this.dsl = dsl;
        this.brapiTrialDAO = brapiTrialDAO;
        this.brAPILocationDAO = brAPILocationDAO;
        this.brAPIStudyDAO = brAPIStudyDAO;
        this.brAPIObservationUnitDAO = brAPIObservationUnitDAO;
        this.brAPIObservationDAO = brAPIObservationDAO;
        this.brAPISeasonDAO = brAPISeasonDAO;
        this.brAPIGermplasmDAO = brAPIGermplasmDAO;
        this.ontologyService = ontologyService;
        this.fileMappingUtil = fileMappingUtil;
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
        // TODO: populate existing observations, assume all new currently
        // key and removing key
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
            Table data,
            Program program,
            User user,
            boolean commit) throws ValidatorException, MissingRequiredInfoException {

        ValidationErrors validationErrors = new ValidationErrors();

        // Get dynamic phenotype columns for processing
        List<Column<?>> dynamicCols = fileMappingUtil.getDynamicColumns(data, EXPERIMENT_TEMPLATE_NAME);
        List<Column<?>> phenotypeCols = new ArrayList<>();
        List<Column<?>> timestampCols = new ArrayList<>();
        for (Column dynamicCol: dynamicCols) {
            //Distinguish between phenotype and timestamp columns
            if (dynamicCol.name().startsWith("TS:")) {
                timestampCols.add(dynamicCol);
            } else {
                phenotypeCols.add(dynamicCol);
            }
        }

        List<String> varNames = phenotypeCols.stream().map(Column::name).collect(Collectors.toList());
        List<String> tsNames = timestampCols.stream().map(Column::name).collect(Collectors.toList());

        // Lookup all traits in system for program, maybe eventually add a variable search in ontology service
        List<Trait> traits = getTraitList(program);

        // filter out just traits specified in file
        List<Trait> filteredTraits = traits.stream()
                .filter(e -> varNames.contains(e.getObservationVariableName()))
                .collect(Collectors.toList());

        // check that all specified ontology terms were found
        if (filteredTraits.size() != varNames.size()) {
            List<String> returnedVarNames = filteredTraits.stream().map(TraitEntity::getObservationVariableName)
                    .collect(Collectors.toList());
            List<String> differences = varNames.stream()
                    .filter(var -> !returnedVarNames.contains(var))
                    .collect(Collectors.toList());
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Ontology term(s) not found: " + String.join(", ", differences));
        }

        // Check that each ts column corresponds to a phenotype column
        List<String> unmatchedTimestamps = tsNames.stream()
                .filter(e -> !(varNames.contains(e.replaceFirst("^TS:\\s*",""))))
                .collect(Collectors.toList());
        if (unmatchedTimestamps.size() > 0) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Timestamp column(s) lack corresponding phenotype column(s): " + String.join(", ", unmatchedTimestamps));
        }

        //Now know timestamps all valid phenotypes, can associate with phenotype column name for easy retrieval
        for (Column tsColumn: timestampCols) {
            timeStampColByPheno.put(tsColumn.name().replaceFirst("^TS:\\s*",""), tsColumn);
        }

        // Perform ontology validations on each observation value in phenotype column
        Map<String, Trait> colVarMap = filteredTraits.stream()
                .collect(Collectors.toMap(Trait::getObservationVariableName, Function.identity()));

        for (Column<?> column : phenotypeCols) {
            for (int i=0; i < column.size(); i++) {
                String value = column.getString(i);
                String colName = column.name();
                validateObservationValue(colVarMap.get(colName), value, colName, validationErrors, i);
            }
        }

        //Timestamp validation
        for (Column<?> column : timestampCols) {
            for (int i=0; i < column.size(); i++) {
                String value = column.getString(i);
                String colName = column.name();
                validateTimeStampValue(value, colName, validationErrors, i);
            }
        }

        // add "New" pending data to the BrapiData objects
        getNewBrapiData(importRows, phenotypeCols, program, user, commit);

        // For each import row
        for (int i = 0; i < importRows.size(); i++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(i);

            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(i, new PendingImport());
            mappedImportRow.setTrial( this.trialByNameNoScope.get( importRow.getExpTitle() ) );
            mappedImportRow.setLocation( this.locationByName.get( importRow.getEnvLocation() ) );
            mappedImportRow.setStudy( this.studyByNameNoScope.get( importRow.getEnv() ) );
            mappedImportRow.setObservationUnit( this.observationUnitByNameNoScope.get( createObservationUnitKey( importRow ) ) );

            // loop over phenotype column observation data for current row
            for (Column<?> column : phenotypeCols) {
                List<PendingImportObject<BrAPIObservation>> observations = mappedImportRow.getObservations();

                // if value was blank won't be entry in map for this observation
                observations.add(this.observationByHash.get(getImportObservationHash(importRow, getVariableNameFromColumn(column))));
            }

            PendingImportObject<BrAPIGermplasm> germplasmPIO = getGidPOI(importRow);
            mappedImportRow.setGermplasm( germplasmPIO );

            if (! StringUtils.isBlank( importRow.getGid() )) { // if GID is blank, don't bother to check if it is valid.
                validateGermplasm(importRow,validationErrors, i, germplasmPIO);
            }

            //Check if existing environment. If so, ObsUnitId must be assigned
            if ((this.studyByNameNoScope.get(importRow.getEnv()).getState() == ImportObjectState.EXISTING) && (StringUtils.isBlank(importRow.getObsUnitID()))){
                throw new MissingRequiredInfoException(MISSING_OBS_UNIT_ID_ERROR);
            }

            mappedBrAPIImport.put(i, mappedImportRow);
        }
        // End-of-loop

        validationErrors = validateFields(importRows, validationErrors);

        if (validationErrors.hasErrors()){
            throw new ValidatorException(validationErrors);
        }

        // Construct our response object
        return getStatisticsMap(importRows);
    }


    private String getVariableNameFromColumn(Column<?> column) {
        // TODO: timestamp stripping?
        return column.name();
    }

    private void getNewBrapiData(List<BrAPIImport> importRows, List<Column<?>> phenotypeCols, Program program, User user, boolean commit) {

        String expSequenceName = program.getExpSequence();
        if (expSequenceName == null) {
            log.error(String.format("Program, %s, is missing a value in the exp sequence column.", program.getName()));
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Program is not properly configured for observation unit import");
        }
        Supplier<BigInteger> expNextVal = () -> dsl.nextval(expSequenceName.toLowerCase());

        String envSequenceName = program.getEnvSequence();
        if (envSequenceName == null) {
            log.error(String.format("Program, %s, is missing a value in the env sequence column.", program.getName()));
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Program is not properly configured for environment import");
        }
        Supplier<BigInteger> envNextVal = () -> dsl.nextval(envSequenceName.toLowerCase());

        for (int i=0; i<importRows.size(); i++) {
            BrAPIImport row = importRows.get(i);
            ExperimentObservation importRow = (ExperimentObservation) row;

            PendingImportObject<BrAPITrial> trialPIO = createTrialPIO(program, user, commit, importRow, expNextVal);
            this.trialByNameNoScope.put(importRow.getExpTitle(), trialPIO);

            String expSeqValue = null;
            if(commit) {
                expSeqValue = trialPIO.getBrAPIObject().getAdditionalInfo().get(BrAPIAdditionalInfoFields.EXPERIMENT_NUMBER).getAsString();
            }

            PendingImportObject<BrAPILocation> locationPIO = createLocationPIO(importRow);
            this.locationByName.put(importRow.getEnvLocation(), locationPIO);

            PendingImportObject<BrAPIStudy> studyPIO = createStudyPIO(program, commit, expSeqValue, importRow, envNextVal);
            this.studyByNameNoScope.put(importRow.getEnv(), studyPIO);

            String envSeqValue = null;
            if(commit) {
                envSeqValue = studyPIO.getBrAPIObject().getAdditionalInfo().get(BrAPIAdditionalInfoFields.ENVIRONMENT_NUMBER).getAsString();
            }

            PendingImportObject<BrAPIObservationUnit> obsUnitPIO = createObsUnitPIO(program, commit, envSeqValue, importRow);
            String key = createObservationUnitKey(importRow);
            this.observationUnitByNameNoScope.put(key, obsUnitPIO);

            for (Column<?> column : phenotypeCols) {
                //If associated timestamp column, add
                String dateTimeValue = null;
                if (timeStampColByPheno.get(column.name()) != null) {
                    dateTimeValue = timeStampColByPheno.get(column.name()).getString(i);
                    //If no timestamp, set to midnight
                    if (!dateTimeValue.isBlank() && !validDateTimeValue(dateTimeValue)){
                        dateTimeValue+="T00:00:00-00:00";
                    }
                }
                //column.name() gets phenotype name
                String seasonDbId = this.yearToSeasonDbId(importRow.getEnvYear(), program.getId());
                PendingImportObject<BrAPIObservation> obsPIO = createObservationPIO(importRow, column.name(), column.getString(i), dateTimeValue, commit, seasonDbId, obsUnitPIO);
                this.observationByHash.put(getImportObservationHash(importRow, getVariableNameFromColumn(column)), obsPIO);
            }
        }
    }

    private String createObservationUnitKey(ExperimentObservation importRow) {
        String key = createObservationUnitKey( importRow.getEnv(), importRow.getExpUnitId() );
        return key;
    }

    private String createObservationUnitKey(String studyName, String obsUnitName) {
        String key = studyName + obsUnitName;
        return key;
    }

    private String getImportObservationHash(ExperimentObservation importRow, String variableName) {
        return getObservationHash(createObservationUnitKey(importRow), variableName, importRow.getEnv());
    }

    private String getObservationHash(String observationUnitName, String variableName, String studyName) {
        String concat = DigestUtils.sha256Hex(observationUnitName) +
                DigestUtils.sha256Hex(variableName) +
                DigestUtils.sha256Hex(studyName);
        return DigestUtils.sha256Hex(concat);
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

        int numNewObservations = Math.toIntExact(
                observationByHash.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.NEW &&
                        !StringUtils.isBlank(preview.getBrAPIObject().getValue()))
                .count()
        );

        ImportPreviewStatistics environmentStats = ImportPreviewStatistics.builder()
                .newObjectCount(environmentNameCounter.size())
                .build();
        ImportPreviewStatistics obdUnitStats = ImportPreviewStatistics.builder()
                .newObjectCount(obsUnitsIDCounter.size())
                .build();
        ImportPreviewStatistics gidStats = ImportPreviewStatistics.builder()
                .newObjectCount(gidCounter.size())
                .build();
        ImportPreviewStatistics observationStats = ImportPreviewStatistics.builder()
                .newObjectCount(numNewObservations)
                .build();

        return Map.of(
                "Environments",         environmentStats,
                "Observation_Units",    obdUnitStats,
                "GIDs",                 gidStats,
                "Observations",         observationStats
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

    private PendingImportObject<BrAPIObservationUnit> createObsUnitPIO(Program program, boolean commit, String seqValue, ExperimentObservation importRow) {
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
            BrAPIObservationUnit newObservationUnit = importRow.constructBrAPIObservationUnit(program, seqValue, commit, germplasmName, BRAPI_REFERENCE_SOURCE, trialID, studyID, id);
            pio = new PendingImportObject<>(ImportObjectState.NEW, newObservationUnit);
        }
        return pio;
    }


    private PendingImportObject<BrAPIObservation> createObservationPIO(ExperimentObservation importRow, String variableName, String value, String timeStampValue, boolean commit, String seasonDbId, PendingImportObject<BrAPIObservationUnit> obsUnitPIO) {
        PendingImportObject<BrAPIObservation> pio = null;
        if (this.observationByHash.containsKey(getImportObservationHash(importRow, variableName))) {
            pio = observationByHash.get(getImportObservationHash(importRow, variableName));
        }
        else {
            BrAPIObservation newObservation = importRow.constructBrAPIObservation(value, variableName, seasonDbId, obsUnitPIO.getBrAPIObject());
            //NOTE: Can't parse invalid timestamp value, so have to skip if invalid.
            // Validation error should be thrown for offending value, but that doesn't happen until later downstream
            if (timeStampValue != null && !timeStampValue.isBlank() && (validDateValue(timeStampValue) || validDateTimeValue(timeStampValue))) {
                newObservation.setObservationTimeStamp(OffsetDateTime.parse(timeStampValue));
            }

            pio = new PendingImportObject<>(ImportObjectState.NEW, newObservation);
        }
        return pio;
    }

    private PendingImportObject<BrAPIStudy> createStudyPIO(Program program, boolean commit, String expSequenceValue, ExperimentObservation importRow, Supplier<BigInteger> envNextVal) {
        PendingImportObject<BrAPIStudy> pio = null;
        if( studyByNameNoScope.containsKey( importRow.getEnv()) ) {
            pio =  studyByNameNoScope.get( importRow.getEnv() ) ;
        }
        else{
            PendingImportObject<BrAPITrial> trialPIO = this.trialByNameNoScope.get(importRow.getExpTitle());
            UUID trialID = trialPIO.getId();
            UUID id = UUID.randomUUID();
            BrAPIStudy newStudy = importRow.constructBrAPIStudy(program, commit, BRAPI_REFERENCE_SOURCE, expSequenceValue, trialID, id, envNextVal);

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

    private PendingImportObject<BrAPITrial> createTrialPIO(Program program, User user, boolean commit, ExperimentObservation importRow, Supplier<BigInteger> expNextVal) {
        PendingImportObject<BrAPITrial> pio = null;
        if( trialByNameNoScope.containsKey( importRow.getExpTitle()) ) {
            pio = trialByNameNoScope.get( importRow.getExpTitle() ) ;
       }
       else {
            UUID id = UUID.randomUUID();
            String expSeqValue = null;
            if(commit){
                expSeqValue = expNextVal.get().toString();
            }
            BrAPITrial newTrial = importRow.constructBrAPITrial(program, user, commit, BRAPI_REFERENCE_SOURCE, id, expSeqValue);
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
        // filter out observations with no 'value' so they will not be saved
        List<BrAPIObservation> newObservations = ProcessorData.getNewObjects(this.observationByHash).stream()
                .filter(obs -> !obs.getValue().isBlank())
                .collect(Collectors.toList());
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

            List<BrAPILocation> createdLocations = new ArrayList<>(brAPILocationDAO.createBrAPILocation(newLocations, program.getId(), upload));
            // set the DbId to the for each newly created trial
            for ( BrAPILocation createdLocation : createdLocations){
                String createdLocationName = createdLocation.getLocationName();
                PendingImportObject<BrAPILocation> pi = this.locationByName.get(createdLocationName);
                BrAPILocation listedLocation = pi.getBrAPIObject();
                String dbid = createdLocation.getLocationDbId();
                listedLocation.setLocationDbId(dbid);
            }

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
            List<BrAPIObservationUnit> createdObservationUnits = brAPIObservationUnitDAO.createBrAPIObservationUnits(newObservationUnits, program.getId(), upload);

            // set the DbId to the for each newly created Observation Unit
            for( BrAPIObservationUnit createdObservationUnit : createdObservationUnits){
                // retrieve the BrAPI ObservationUnit from this.observationUnitByNameNoScope
                String createdObservationUnit_StripedStudyName = Utilities.removeProgramKeyAndUnknownAdditionalData( createdObservationUnit.getStudyName(),program.getKey() );
                String createdObservationUnit_StripedObsUnitName = Utilities.removeProgramKeyAndUnknownAdditionalData( createdObservationUnit.getObservationUnitName(),program.getKey() );
                String createdObsUnit_key = createObservationUnitKey(createdObservationUnit_StripedStudyName, createdObservationUnit_StripedObsUnitName);
                PendingImportObject<BrAPIObservationUnit> pi = this.observationUnitByNameNoScope.get(createdObsUnit_key);
                // update the retrieved BrAPI object
                BrAPIObservationUnit brAPIObservationUnit = pi.getBrAPIObject();
                brAPIObservationUnit.setObservationUnitDbId ( createdObservationUnit.getObservationUnitDbId() );
            }
         
            updateObservationDependencyValues(program);
            brAPIObservationDAO.createBrAPIObservation(newObservations, program.getId(), upload);
        } catch (ApiException e) {
            log.error(e.getResponseBody());
            throw new InternalServerException(e.toString(), e);
        }
    }

    private void updateObservationDependencyValues(Program program) {
        String programKey = program.getKey();

        // update the observations study DbIds, Observation Unit DbIds and Germplasm DbIds
        this.observationUnitByNameNoScope.values().stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(obsUnit -> updateObservationDbIds(obsUnit, programKey));

        // Update ObservationVariable DbIds
        List<Trait> traits = getTraitList(program);
        Map<String, Trait> traitMap = traits.stream().collect(Collectors.toMap(trait -> trait.getObservationVariableName(), trait -> trait));

        for(PendingImportObject<BrAPIObservation> observation: this.observationByHash.values()){
            String observationVariableName = observation.getBrAPIObject().getObservationVariableName();
            if( observationVariableName!=null && traitMap.containsKey(observationVariableName)){
                String observationVariableDbId = traitMap.get(observationVariableName).getObservationVariableDbId();
                observation.getBrAPIObject().setObservationVariableDbId( observationVariableDbId );
            }
        }
    }

    private List<Trait> getTraitList(Program program) {
        List<Trait> traits = null;
        try {
            traits = ontologyService.getTraitsByProgramId(program.getId(), true);
        } catch (DoesNotExistException e) {
            log.error(e.getMessage(), e);
            throw new InternalServerException(e.toString(), e);
        }
        return traits;
    }

    private void updateObservationDbIds(BrAPIObservationUnit obsUnit, String programKey) {
        // Match on Env and Exp Unit ID
        this.observationByHash.values().stream()
                              .filter(obs ->
                                      obs.getBrAPIObject()
                                                .getAdditionalInfo() != null && obs.getBrAPIObject()
                                                                                   .getAdditionalInfo()
                                                                                   .get(BrAPIAdditionalInfoFields.STUDY_NAME) != null
                                      && obs.getBrAPIObject()
                                         .getAdditionalInfo()
                                         .get(BrAPIAdditionalInfoFields.STUDY_NAME)
                                         .getAsString()
                                         .equals(obsUnit.getStudyName())
                                      && Utilities.removeProgramKeyAndUnknownAdditionalData(obs.getBrAPIObject()
                                                                                               .getObservationUnitName(), programKey)
                                                  .equals(Utilities.removeProgramKeyAndUnknownAdditionalData(obsUnit.getObservationUnitName(), programKey)))
                .forEach(obs -> {
                    if(StringUtils.isBlank(obs.getBrAPIObject().getObservationUnitDbId())) {
                        obs.getBrAPIObject().setObservationUnitDbId(obsUnit.getObservationUnitDbId());
                    }
                    obs.getBrAPIObject().setStudyDbId(obsUnit.getStudyDbId());
                    obs.getBrAPIObject().setGermplasmDbId(obsUnit.getGermplasmDbId());
                });
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
                .filter(study -> location.getLocationName().equals( study.getBrAPIObject().getLocationName() ))
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

        UUID experimentId = trial.getId();

        List<BrAPIStudy> existingStudies;
        try {
            existingStudies = brAPIStudyDAO.getStudiesByExperimentID(experimentId, program);
            existingStudies.forEach(existingStudy -> {
                //Swap season DbId with year String
                String seasonId = existingStudy.getSeasons().get(0);
                existingStudy.setSeasons( List.of( this.seasonDbIdToYear( seasonId, program.getId() ) ) );

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

                //get TrialId from existingTrial
                List<BrAPIExternalReference> experimentRefs = existingTrial.getExternalReferences();
                Optional <BrAPIExternalReference> experimentIDRef = experimentRefs.stream()
                        .filter(this::isTrialRefSource)
                        .findFirst();
                if( experimentIDRef.isEmpty()){
                    throw new InternalServerException("An Experiment ID was not found any of the external references");
                }
                UUID experimentId = UUID.fromString(  experimentIDRef.get().getReferenceID() );

                trialByNameNoScope.put(
                        existingTrial.getTrialName(),
                        new PendingImportObject<>(ImportObjectState.EXISTING, existingTrial, experimentId));
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

    private void validateTimeStampValue(String value,
                                        String columnHeader, ValidationErrors validationErrors, int row){
        if(StringUtils.isBlank(value)) {
            log.debug(String.format("skipping validation of observation timestamp because there is no value.\n\tvariable: %s\n\trow: %d", columnHeader, row));
            return;
        }
        if (!validDateValue(value) && !validDateTimeValue(value)) {
            addRowError(columnHeader, "Incorrect datetime format detected. Expected YYYY-MM-DD or YYYY-MM-DDThh:mm:ss+hh:mm", validationErrors, row);
        }

    }
    private void validateObservationValue(Trait variable, String value,
                                          String columnHeader, ValidationErrors validationErrors, int row) {
        if(StringUtils.isBlank(value)) {
            log.debug(String.format("skipping validation of observation because there is no value.\n\tvariable: %s\n\trow: %d", variable.getObservationVariableName(), row));
            return;
        }

        switch(variable.getScale().getDataType()) {
            case NUMERICAL:
                Optional<BigDecimal> number = validNumericValue(value);
                if (number.isEmpty()) {
                    addRowError(columnHeader, "Non-numeric text detected detected", validationErrors, row);
                }
                else if (!validNumericRange(number.get(), variable.getScale())) {
                    addRowError(columnHeader, "Value outside of min/max range detected", validationErrors, row);
                }
                break;
            case DATE:
                if (!validDateValue(value)) {
                    addRowError(columnHeader, "Incorrect date format detected. Expected YYYY-MM-DD", validationErrors, row);
                }
                break;
            case ORDINAL:
                if (!validCategory(variable.getScale().getCategories(), value)) {
                    addRowError(columnHeader, "Undefined ordinal category detected", validationErrors, row);
                }
                break;
            case NOMINAL:
                if (!validCategory(variable.getScale().getCategories(), value)) {
                    addRowError(columnHeader, "Undefined nominal category detected", validationErrors, row);
                }
                break;
            default:
                break;
        }

    }
    private Optional<BigDecimal> validNumericValue(String value) {
        BigDecimal number;
        try {
            number = new BigDecimal(value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.of(number);
    }

    private boolean validNumericRange(BigDecimal value, Scale validValues) {
        // account for empty min or max in valid determination
        return (validValues.getValidValueMin() == null || value.compareTo(BigDecimal.valueOf(validValues.getValidValueMin())) >= 0) &&
               (validValues.getValidValueMax() == null || value.compareTo(BigDecimal.valueOf(validValues.getValidValueMax())) <= 0);
    }

    private boolean validDateValue(String value) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
        try {
            formatter.parse(value);
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

    private boolean validDateTimeValue(String value) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        try {
            formatter.parse(value);
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

    private boolean validCategory(List<BrAPIScaleValidValuesCategories> categories, String value) {
        Set<String> categoryValues = categories.stream().map(category -> category.getValue().toLowerCase()).collect(Collectors.toSet());
        return categoryValues.contains(value.toLowerCase());
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
        return this.yearToSeasonDbId(year, programId);
    }

    /**
     * Converts year String to SeasonDbId
     * <br>
     * NOTE: This assumes that the only Season records of interest are ones
     * with a blank name or a name that is the same as the year.
     *
     * @param year The year as a string
     * @param programId the program ID.
     * @return the DbId of the season-record associated with the year
     */
    private String yearToSeasonDbId(String year, UUID programId) {
        String dbID = null;
        if (this.yearToSeasonDbIdCache.containsKey(year) ){ // get it from cache if possible
            dbID = this.yearToSeasonDbIdCache.get(year);
        }
        else{
            dbID = this.yearToSeasonDbIdFromDatabase(year,programId);
            this.yearToSeasonDbIdCache.put(year, dbID);
        }
        return dbID;
    }


    private String seasonDbIdToYear(String seasonDbId, UUID programId) {
        String year = null;
        if (this.seasonDbIdToYearCache.containsKey(seasonDbId) ){ // get it from cache if possible
            year = this.seasonDbIdToYearCache.get(seasonDbId);
        }
        else{
            year = this.seasonDbIdToYearFromDatabase(seasonDbId,programId);
            this.seasonDbIdToYearCache.put(seasonDbId, year);
        }
        return year;
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
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            log.error(e.getResponseBody(), e);
        }

        String seasonDbId = (targetSeason==null) ? null : targetSeason.getSeasonDbId();
        return seasonDbId;
    }

    private String seasonDbIdToYearFromDatabase(String seasonDbId, UUID programId) {
        BrAPISeason targetSeason = null;
        BrAPISeason season = null;
        try {
            season = this.brAPISeasonDAO.getSeasonById (seasonDbId, programId);
        } catch (ApiException e) {
            log.error(e.getResponseBody(), e);
        }
        Integer yearInt = (season == null) ? null : season.getYear();
        String yearStr = (yearInt==null) ? "" : yearInt.toString();
        return yearStr;
    }

    private boolean isTrialRefSource(BrAPIExternalReference brAPIExternalReference) {
        return brAPIExternalReference.getReferenceSource().equals( String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName()) );
    }
}