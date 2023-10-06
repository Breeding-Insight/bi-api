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
import org.brapi.v2.model.core.*;
import org.brapi.v2.model.core.request.BrAPIListNewRequest;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.ProgramLocationRequest;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.daos.*;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.base.AdditionalInfo;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation.Columns;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.FileMappingUtil;
import org.breedinginsight.dao.db.tables.pojos.TraitEntity;
import org.breedinginsight.model.*;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.ProgramLocationService;
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
    private static final String MISSING_OBS_UNIT_ID_ERROR = "Experiment Units are missing Observation Unit Id.<br/><br/>" +
            "If you’re trying to add these units to the experiment, please create a new environment" +
            " with all appropriate experiment units (NOTE: this will generate new Observation Unit Ids " +
            "for each experiment unit).";
    private static final String MIDNIGHT = "T00:00:00-00:00";
    private static final String TIMESTAMP_PREFIX = "TS:";
    private static final String TIMESTAMP_REGEX = "^"+TIMESTAMP_PREFIX+"\\s*";
    private static final String COMMA_DELIMITER = ",";
    private static final String BLANK_FIELD_EXPERIMENT = "Field is blank when creating a new experiment";
    private static final String BLANK_FIELD_ENV = "Field is blank when creating a new environment";
    private static final String BLANK_FIELD_OBS = "Field is blank when creating new observations";
    private static final String ENV_LOCATION_MISMATCH = "All locations must be the same for a given environment";
    private static final String ENV_YEAR_MISMATCH = "All years must be the same for a given environment";

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    private final DSLContext dsl;
    private final BrAPITrialDAO brapiTrialDAO;
    private final ProgramLocationService locationService;
    private final BrAPIStudyDAO brAPIStudyDAO;
    private final BrAPIObservationUnitDAO brAPIObservationUnitDAO;
    private final BrAPIObservationDAO brAPIObservationDAO;
    private final BrAPISeasonDAO brAPISeasonDAO;
    private final BrAPIGermplasmDAO brAPIGermplasmDAO;
    private final BrAPIListDAO brAPIListDAO;
    private final OntologyService ontologyService;
    private final FileMappingUtil fileMappingUtil;


    // used to make the yearsToSeasonDbId() function more efficient
    private final Map<String, String> yearToSeasonDbIdCache = new HashMap<>();
    // used to make the seasonDbIdtoYear() function more efficient
    private final Map<String, String> seasonDbIdToYearCache = new HashMap<>();

    //These BrapiData-objects are initially populated by the getExistingBrapiData() method,
    // then updated by the getNewBrapiData() method.
    private Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope = null;
    private Map<String, PendingImportObject<ProgramLocation>> locationByName = null;
    private Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope = null;
    private Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName = null;
    //  It is assumed that there are no preexisting Observation Units for the given environment (so this will not be
    // initialized by getExistingBrapiData() )
    private Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope = null;

    private Map<String, PendingImportObject<BrAPIObservation>> observationByHash = new HashMap<>();

    // existingGermplasmByGID is populated by getExistingBrapiData(), but not updated by the getNewBrapiData() method
    private Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = null;

    // Associates timestamp columns to associated phenotype column name for ease of storage
    private Map<String, Column<?>> timeStampColByPheno = new HashMap<>();

    @Inject
    public ExperimentProcessor(DSLContext dsl,
                               BrAPITrialDAO brapiTrialDAO,
                               ProgramLocationService locationService,
                               BrAPIStudyDAO brAPIStudyDAO,
                               BrAPIObservationUnitDAO brAPIObservationUnitDAO,
                               BrAPIObservationDAO brAPIObservationDAO,
                               BrAPISeasonDAO brAPISeasonDAO,
                               BrAPIGermplasmDAO brAPIGermplasmDAO,
                               BrAPIListDAO brAPIListDAO, OntologyService ontologyService,
                               FileMappingUtil fileMappingUtil) {
        this.dsl = dsl;
        this.brapiTrialDAO = brapiTrialDAO;
        this.locationService = locationService;
        this.brAPIStudyDAO = brAPIStudyDAO;
        this.brAPIObservationUnitDAO = brAPIObservationUnitDAO;
        this.brAPIObservationDAO = brAPIObservationDAO;
        this.brAPISeasonDAO = brAPISeasonDAO;
        this.brAPIGermplasmDAO = brAPIGermplasmDAO;
        this.brAPIListDAO = brAPIListDAO;
        this.ontologyService = ontologyService;
        this.fileMappingUtil = fileMappingUtil;
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Initialize the Map<String, PendingImportObject> objects with existing BrAPI Data.
     *
     * @param importRows
     * @param program
     */
    @Override
    public void getExistingBrapiData(List<BrAPIImport> importRows, Program program) {

        List<ExperimentObservation> experimentImportRows = importRows.stream()
                                                                     .map(trialImport -> (ExperimentObservation) trialImport)
                                                                     .collect(Collectors.toList());

        this.observationUnitByNameNoScope = initializeObservationUnits(program, experimentImportRows);
        this.trialByNameNoScope = initializeTrialByNameNoScope(program, experimentImportRows);
        this.studyByNameNoScope = initializeStudyByNameNoScope(program, experimentImportRows);
        this.locationByName = initializeUniqueLocationNames(program, experimentImportRows);
        this.obsVarDatasetByName = initializeObsVarDatasetByName(program, experimentImportRows);
        this.existingGermplasmByGID = initializeExistingGermplasmByGID(program, experimentImportRows);
    }

    /**
     * @param importRows        - one element of the list for every row of the import file.
     * @param mappedBrAPIImport - passed in by reference and modified within this program (this will later be passed to the front end for the preview)
     * @param program
     * @param user
     * @param commit            -  true when the data should be saved (ie when the user has pressed the "Commit" button)
     *                          false when used for preview only
     * @return Map<String, ImportPreviewStatistics> - used to display the summary statistics.
     * @throws ValidatorException
     */
    @Override
    public Map<String, ImportPreviewStatistics> process(
            ImportUpload upload,
            List<BrAPIImport> importRows,
            Map<Integer, PendingImport> mappedBrAPIImport,
            Table data,
            Program program,
            User user,
            boolean commit) throws ValidatorException, MissingRequiredInfoException, ApiException {
        log.debug("processing experiment import");

        ValidationErrors validationErrors = new ValidationErrors();

        // Get dynamic phenotype columns for processing
        List<Column<?>> dynamicCols = data.columns(upload.getDynamicColumnNames());
        List<Column<?>> phenotypeCols = new ArrayList<>();
        List<Column<?>> timestampCols = new ArrayList<>();
        for (Column<?> dynamicCol : dynamicCols) {
            //Distinguish between phenotype and timestamp columns
            if (dynamicCol.name().startsWith(TIMESTAMP_PREFIX)) {
                timestampCols.add(dynamicCol);
            } else {
                phenotypeCols.add(dynamicCol);
            }
        }

        List<Trait> referencedTraits = verifyTraits(program.getId(), phenotypeCols, timestampCols, validationErrors);

        //Now know timestamps all valid phenotypes, can associate with phenotype column name for easy retrieval
        for (Column<?> tsColumn : timestampCols) {
            timeStampColByPheno.put(tsColumn.name().replaceFirst(TIMESTAMP_REGEX, StringUtils.EMPTY), tsColumn);
        }

        // add "New" pending data to the BrapiData objects
        initNewBrapiData(importRows, phenotypeCols, program, user, referencedTraits, commit);

        prepareDataForValidation(importRows, phenotypeCols, mappedBrAPIImport);

        validateFields(importRows, validationErrors, mappedBrAPIImport, referencedTraits, program, phenotypeCols, commit);

        if (validationErrors.hasErrors()) {
            throw new ValidatorException(validationErrors);
        }

        log.debug("done processing experiment import");
        // Construct our response object
        return generateStatisticsMap(importRows);
    }

    @Override
    public void validateDependencies(Map<Integer, PendingImport> mappedBrAPIImport) throws ValidatorException {
        // TODO
    }

    @Override
    public void postBrapiData(Map<Integer, PendingImport> mappedBrAPIImport, Program program, ImportUpload upload) {
        log.debug("starting post of experiment data to BrAPI server");

        List<BrAPITrial> newTrials = ProcessorData.getNewObjects(this.trialByNameNoScope);
        Map<String, BrAPITrial> mutatedTrialsById = ProcessorData
                .getMutationsByObjectId(trialByNameNoScope, trial -> trial.getTrialDbId());

        List<ProgramLocationRequest> newLocations = ProcessorData.getNewObjects(this.locationByName)
                                                                 .stream()
                                                                 .map(location -> ProgramLocationRequest.builder()
                                                                                                             .name(location.getName())
                                                                                                             .build())
                                                                 .collect(Collectors.toList());
        List<BrAPIStudy> newStudies = ProcessorData.getNewObjects(this.studyByNameNoScope);

        List<BrAPIListNewRequest> newDatasetRequests = ProcessorData.getNewObjects(obsVarDatasetByName).stream().map(details -> {
            BrAPIListNewRequest request = new BrAPIListNewRequest();
            request.setListName(details.getListName());
            request.setListType(details.getListType());
            request.setExternalReferences(details.getExternalReferences());
            request.setAdditionalInfo(details.getAdditionalInfo());
            request.data(details.getData());
            return request;
        }).collect(Collectors.toList());
        Map<String, BrAPIListDetails> datasetNewDataById = ProcessorData
                .getMutationsByObjectId(obsVarDatasetByName, listDetails -> listDetails.getListDbId());

        List<BrAPIObservationUnit> newObservationUnits = ProcessorData.getNewObjects(this.observationUnitByNameNoScope);
        // filter out observations with no 'value' so they will not be saved
        List<BrAPIObservation> newObservations = ProcessorData.getNewObjects(this.observationByHash)
                                                              .stream()
                                                              .filter(obs -> !obs.getValue().isBlank())
                                                              .collect(Collectors.toList());

        AuthenticatedUser actingUser = new AuthenticatedUser(upload.getUpdatedByUser().getName(), new ArrayList<>(), upload.getUpdatedByUser().getId(), new ArrayList<>());

        try {
            List<BrAPIListSummary> createdDatasets = new ArrayList<>(brAPIListDAO.createBrAPILists(newDatasetRequests, program.getId(), upload));
            createdDatasets.forEach(summary -> {
                obsVarDatasetByName.get(summary.getListName()).getBrAPIObject().setListDbId(summary.getListDbId());
            });

            List<BrAPITrial> createdTrials = new ArrayList<>(brapiTrialDAO.createBrAPITrials(newTrials, program.getId(), upload));
            // set the DbId to the for each newly created trial
            for (BrAPITrial createdTrial : createdTrials) {
                String createdTrialName = Utilities.removeProgramKey(createdTrial.getTrialName(), program.getKey());
                this.trialByNameNoScope.get(createdTrialName)
                        .getBrAPIObject()
                        .setTrialDbId(createdTrial.getTrialDbId());
            }

            List<ProgramLocation> createdLocations = new ArrayList<>(locationService.create(actingUser, program.getId(), newLocations));
            // set the DbId to the for each newly created trial
            for (ProgramLocation createdLocation : createdLocations) {
                String createdLocationName = createdLocation.getName();
                this.locationByName.get(createdLocationName)
                                   .getBrAPIObject()
                                   .setLocationDbId(createdLocation.getLocationDbId());
            }

            updateStudyDependencyValues(mappedBrAPIImport, program.getKey());
            List<BrAPIStudy> createdStudies = brAPIStudyDAO.createBrAPIStudies(newStudies, program.getId(), upload);

            // set the DbId to the for each newly created study
            for (BrAPIStudy createdStudy : createdStudies) {
                String createdStudy_name_no_key = Utilities.removeProgramKeyAndUnknownAdditionalData(createdStudy.getStudyName(), program.getKey());
                this.studyByNameNoScope.get(createdStudy_name_no_key)
                                       .getBrAPIObject()
                                       .setStudyDbId(createdStudy.getStudyDbId());
            }

            updateObsUnitDependencyValues(program.getKey());
            List<BrAPIObservationUnit> createdObservationUnits = brAPIObservationUnitDAO.createBrAPIObservationUnits(newObservationUnits, program.getId(), upload);

            // set the DbId to the for each newly created Observation Unit
            for (BrAPIObservationUnit createdObservationUnit : createdObservationUnits) {
                // retrieve the BrAPI ObservationUnit from this.observationUnitByNameNoScope
                String createdObservationUnit_StripedStudyName = Utilities.removeProgramKeyAndUnknownAdditionalData(createdObservationUnit.getStudyName(), program.getKey());
                String createdObservationUnit_StripedObsUnitName = Utilities.removeProgramKeyAndUnknownAdditionalData(createdObservationUnit.getObservationUnitName(), program.getKey());
                String createdObsUnit_key = createObservationUnitKey(createdObservationUnit_StripedStudyName, createdObservationUnit_StripedObsUnitName);
                this.observationUnitByNameNoScope.get(createdObsUnit_key)
                                                 .getBrAPIObject()
                                                 .setObservationUnitDbId(createdObservationUnit.getObservationUnitDbId());
            }

            updateObservationDependencyValues(program);
            brAPIObservationDAO.createBrAPIObservations(newObservations, program.getId(), upload);
        } catch (ApiException e) {
            log.error("Error saving experiment import: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException("Error saving experiment import", e);
        } catch (Exception e) {
            log.error("Error saving experiment import", e);
            throw new InternalServerException(e.getMessage(), e);
        }

        mutatedTrialsById.forEach((id, trial) ->  {
            try {
                brapiTrialDAO.updateBrAPITrial(id, trial, program.getId());
            } catch (ApiException e) {
                log.error("Error updating dataset observation variables: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException("Error saving experiment import", e);
            } catch (Exception e) {
                log.error("Error updating dataset observation variables: ", e);
                throw new InternalServerException(e.getMessage(), e);
            }
        });

        datasetNewDataById.forEach((id, dataset) -> {
            try {
                List<String> existingObsVarIds = brAPIListDAO.getListById(id, program.getId()).getResult().getData();
                List<String> newObsVarIds = dataset
                        .getData()
                        .stream()
                        .filter(obsVarId -> !existingObsVarIds.contains(obsVarId)).collect(Collectors.toList());
                List<String> obsVarIds = new ArrayList<>(existingObsVarIds);
                obsVarIds.addAll(newObsVarIds);
                dataset.setData(obsVarIds);
                brAPIListDAO.updateBrAPIList(id, dataset, program.getId());
            } catch (ApiException e) {
                log.error("Error updating dataset observation variables: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException("Error saving experiment import", e);
            } catch (Exception e) {
                log.error("Error updating dataset observation variables: ", e);
                throw new InternalServerException(e.getMessage(), e);
            }
        });
        log.debug("experiment import complete");

    }

    private void prepareDataForValidation(List<BrAPIImport> importRows, List<Column<?>> phenotypeCols, Map<Integer, PendingImport> mappedBrAPIImport) {
        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);

            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(rowNum, new PendingImport());
            mappedImportRow.setTrial(this.trialByNameNoScope.get(importRow.getExpTitle()));
            mappedImportRow.setLocation(this.locationByName.get(importRow.getEnvLocation()));
            mappedImportRow.setStudy(this.studyByNameNoScope.get(importRow.getEnv()));
            mappedImportRow.setObservationUnit(this.observationUnitByNameNoScope.get(createObservationUnitKey(importRow)));

            // loop over phenotype column observation data for current row
            List<PendingImportObject<BrAPIObservation>> observations = mappedImportRow.getObservations();
            for (Column<?> column : phenotypeCols) {
                // if value was blank won't be entry in map for this observation
                observations.add(this.observationByHash.get(getImportObservationHash(importRow, getVariableNameFromColumn(column))));
            }

            PendingImportObject<BrAPIGermplasm> germplasmPIO = getGidPOI(importRow);
            mappedImportRow.setGermplasm(germplasmPIO);

            mappedBrAPIImport.put(rowNum, mappedImportRow);
        }
    }

    private List<Trait> verifyTraits(UUID programId, List<Column<?>> phenotypeCols, List<Column<?>> timestampCols, ValidationErrors validationErrors) {
        Set<String> varNames = phenotypeCols.stream()
                                            .map(Column::name)
                                            .collect(Collectors.toSet());
        Set<String> tsNames = timestampCols.stream()
                                           .map(Column::name)
                                           .collect(Collectors.toSet());

        // filter out just traits specified in file
        List<Trait> filteredTraits = fetchFileTraits(programId, varNames);

        // check that all specified ontology terms were found
        if (filteredTraits.size() != varNames.size()) {
            Set<String> returnedVarNames = filteredTraits.stream()
                                                         .map(TraitEntity::getObservationVariableName)
                                                         .collect(Collectors.toSet());
            List<String> differences = varNames.stream()
                                               .filter(var -> !returnedVarNames.contains(var))
                                               .collect(Collectors.toList());
            //TODO convert this to a ValidationError
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                                          "Ontology term(s) not found: " + String.join(COMMA_DELIMITER, differences));
        }

        // Check that each ts column corresponds to a phenotype column
        List<String> unmatchedTimestamps = tsNames.stream()
                                                  .filter(e -> !(varNames.contains(e.replaceFirst(TIMESTAMP_REGEX, StringUtils.EMPTY))))
                                                  .collect(Collectors.toList());
        if (unmatchedTimestamps.size() > 0) {
            //TODO convert this to a ValidationError
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                                          "Timestamp column(s) lack corresponding phenotype column(s): " + String.join(COMMA_DELIMITER, unmatchedTimestamps));
        }

        // sort the verified traits to match the order of the trait columns
        List<String> phenotypeColNames = phenotypeCols.stream().map(Column::name).collect(Collectors.toList());
        return fileMappingUtil.sortByField(phenotypeColNames, filteredTraits, TraitEntity::getObservationVariableName);
    }

    private List<Trait> fetchFileTraits(UUID programId, Collection<String> varNames) {
        try {
            List<Trait> traits = ontologyService.getTraitsByProgramId(programId, true);
            // filter out just traits specified in file
            return traits.stream()
                         .filter(e -> varNames.contains(e.getObservationVariableName()))
                         .collect(Collectors.toList());
        } catch (DoesNotExistException e) {
            log.error(e.getMessage(), e);
            throw new InternalServerException(e.toString(), e);
        }
    }

    private String getVariableNameFromColumn(Column<?> column) {
        // TODO: timestamp stripping?
        return column.name();
    }

    private void initNewBrapiData(List<BrAPIImport> importRows, List<Column<?>> phenotypeCols, Program program, User user, List<Trait> referencedTraits, boolean commit) {

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

        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);

            PendingImportObject<BrAPITrial> trialPIO = fetchOrCreateTrialPIO(program, user, commit, importRow, expNextVal);

            String expSeqValue = null;
            if (commit) {
                expSeqValue = trialPIO.getBrAPIObject()
                                      .getAdditionalInfo()
                                      .get(BrAPIAdditionalInfoFields.EXPERIMENT_NUMBER)
                                      .getAsString();
            }

            if (commit) {
                fetchOrCreateDatasetPIO(importRow, program, referencedTraits);
            }

            fetchOrCreateLocationPIO(importRow);

            PendingImportObject<BrAPIStudy> studyPIO = fetchOrCreateStudyPIO(program, commit, expSeqValue, importRow, envNextVal);

            String envSeqValue = null;
            if (commit) {
                envSeqValue = studyPIO.getBrAPIObject()
                                      .getAdditionalInfo()
                                      .get(BrAPIAdditionalInfoFields.ENVIRONMENT_NUMBER)
                                      .getAsString();
            }

            PendingImportObject<BrAPIObservationUnit> obsUnitPIO = fetchOrCreateObsUnitPIO(program, commit, envSeqValue, importRow);

            for (Column<?> column : phenotypeCols) {
                //If associated timestamp column, add
                String dateTimeValue = null;
                if (timeStampColByPheno.containsKey(column.name())) {
                    dateTimeValue = timeStampColByPheno.get(column.name()).getString(rowNum);
                    //If no timestamp, set to midnight
                    if (!dateTimeValue.isBlank() && !validDateTimeValue(dateTimeValue)) {
                        dateTimeValue += MIDNIGHT;
                    }
                }
                //column.name() gets phenotype name
                String seasonDbId = this.yearToSeasonDbId(importRow.getEnvYear(), program.getId());
                fetchOrCreateObservationPIO(program, user, importRow, column.name(), column.getString(rowNum), dateTimeValue, commit, seasonDbId, obsUnitPIO);
            }
        }
    }

    private String createObservationUnitKey(ExperimentObservation importRow) {
        return createObservationUnitKey(importRow.getEnv(), importRow.getExpUnitId());
    }

    private String createObservationUnitKey(String studyName, String obsUnitName) {
        return studyName + obsUnitName;
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

    private ValidationErrors validateFields(List<BrAPIImport> importRows, ValidationErrors validationErrors, Map<Integer, PendingImport> mappedBrAPIImport, List<Trait> referencedTraits, Program program,
                                            List<Column<?>> phenotypeCols, boolean commit) throws MissingRequiredInfoException, ApiException {
        //fetching any existing observations for any OUs in the import
        Map<String, BrAPIObservation> existingObsByObsHash = fetchExistingObservations(referencedTraits, program);
        Map<String, Trait> colVarMap = referencedTraits.stream().collect(Collectors.toMap(Trait::getObservationVariableName, Function.identity()));

        Set<String> uniqueStudyAndObsUnit = new HashSet<>();
        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);
            PendingImport mappedImportRow = mappedBrAPIImport.get(rowNum);

            if (StringUtils.isNotBlank(importRow.getGid())) { // if GID is blank, don't bother to check if it is valid.
                validateGermplasm(importRow, validationErrors, rowNum, mappedImportRow.getGermplasm());
            }
            validateTestOrCheck(importRow, validationErrors, rowNum);

            //Check if existing environment. If so, ObsUnitId must be assigned
            if ((mappedImportRow.getStudy().getState() == ImportObjectState.EXISTING)
                    && (StringUtils.isBlank(importRow.getObsUnitID()))) {
                throw new MissingRequiredInfoException(MISSING_OBS_UNIT_ID_ERROR);
            }

            validateConditionallyRequired(validationErrors, rowNum, importRow, program, commit);
            validateObservationUnits(validationErrors, uniqueStudyAndObsUnit, rowNum, importRow);
            validateObservations(validationErrors, rowNum, importRow, phenotypeCols, colVarMap, existingObsByObsHash);
        }
        return validationErrors;
    }

    private void validateObservationUnits(ValidationErrors validationErrors, Set<String> uniqueStudyAndObsUnit, int rowNum, ExperimentObservation importRow) {
        validateUniqueObsUnits(validationErrors, uniqueStudyAndObsUnit, rowNum, importRow);

        String key = createObservationUnitKey(importRow);
        PendingImportObject<BrAPIObservationUnit> ouPIO = observationUnitByNameNoScope.get(key);
        if(ouPIO.getState() == ImportObjectState.NEW && StringUtils.isNotBlank(importRow.getObsUnitID())) {
            addRowError(Columns.OBS_UNIT_ID, "Could not find observation unit by ObsUnitDBID", validationErrors, rowNum);
        }
    }

    private Map<String, BrAPIObservation> fetchExistingObservations(List<Trait> referencedTraits, Program program) throws ApiException {
        Set<String> ouDbIds = new HashSet<>();
        Set<String> variableDbIds = new HashSet<>();
        Map<String, String> variableNameByDbId = new HashMap<>();
        Map<String, String> ouNameByDbId = new HashMap<>();
        Map<String, String> studyNameByDbId = studyByNameNoScope.values()
                                                                .stream()
                                                                .filter(pio -> StringUtils.isNotBlank(pio.getBrAPIObject().getStudyDbId()))
                                                                .map(PendingImportObject::getBrAPIObject)
                                                                .collect(Collectors.toMap(BrAPIStudy::getStudyDbId, brAPIStudy -> Utilities.removeProgramKeyAndUnknownAdditionalData(brAPIStudy.getStudyName(), program.getKey())));

        observationUnitByNameNoScope.values().forEach(ou -> {
            if(StringUtils.isNotBlank(ou.getBrAPIObject().getObservationUnitDbId())) {
                ouDbIds.add(ou.getBrAPIObject().getObservationUnitDbId());
            }
            ouNameByDbId.put(ou.getBrAPIObject().getObservationUnitDbId(), Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getBrAPIObject().getObservationUnitName(), program.getKey()));
        });

        for (Trait referencedTrait : referencedTraits) {
            variableDbIds.add(referencedTrait.getObservationVariableDbId());
            variableNameByDbId.put(referencedTrait.getObservationVariableDbId(), referencedTrait.getObservationVariableName());
        }

        List<BrAPIObservation> existingObservations = brAPIObservationDAO.getObservationsByObservationUnitsAndVariables(ouDbIds, variableDbIds, program);

        return existingObservations.stream()
                                   .map(obs -> {
                                       String studyName = studyNameByDbId.get(obs.getStudyDbId());
                                       String variableName = variableNameByDbId.get(obs.getObservationVariableDbId());
                                       String ouName = ouNameByDbId.get(obs.getObservationUnitDbId());

                                       String key = getObservationHash(createObservationUnitKey(studyName, ouName), variableName, studyName);

                                       return Map.entry(key, obs);
                                   })
                                   .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void validateObservations(ValidationErrors validationErrors, int rowNum, ExperimentObservation importRow, List<Column<?>> phenotypeCols, Map<String, Trait> colVarMap, Map<String, BrAPIObservation> existingObservations) {
        phenotypeCols.forEach(phenoCol -> {
            var importHash = getImportObservationHash(importRow, phenoCol.name());
            if(existingObservations.containsKey(importHash) && StringUtils.isNotBlank(phenoCol.getString(rowNum)) && !existingObservations.get(importHash).getValue().equals(phenoCol.getString(rowNum))) {
                addRowError(
                        phenoCol.name(),
                        String.format("Value already exists for ObsUnitId: %s, Phenotype: %s", importRow.getObsUnitID(), phenoCol.name()),
                        validationErrors, rowNum
                );
            } else if(existingObservations.containsKey(importHash) && (StringUtils.isBlank(phenoCol.getString(rowNum)) || existingObservations.get(importHash).getValue().equals(phenoCol.getString(rowNum)))) {
                BrAPIObservation existingObs = existingObservations.get(importHash);
                existingObs.setObservationVariableName(phenoCol.name());
                observationByHash.get(importHash).setState(ImportObjectState.EXISTING);
                observationByHash.get(importHash).setBrAPIObject(existingObs);
            } else {
                validateObservationValue(colVarMap.get(phenoCol.name()), phenoCol.getString(rowNum), phenoCol.name(), validationErrors, rowNum);

                //Timestamp validation
                if(timeStampColByPheno.containsKey(phenoCol.name())) {
                    Column<?> timeStampCol = timeStampColByPheno.get(phenoCol.name());
                    validateTimeStampValue(timeStampCol.getString(rowNum), timeStampCol.name(), validationErrors, rowNum);
                }
            }
        });
    }

    /**
     * Validate that the observation unit is unique within a study.
     * <br>
     * SIDE EFFECTS:  validationErrors and uniqueStudyAndObsUnit can be modified.
     *
     * @param validationErrors      can be modified as a side effect.
     * @param uniqueStudyAndObsUnit can be modified as a side effect.
     * @param rowNum                     counter that is always two less the file row being validated
     * @param importRow             the data row being validated
     */
    private void validateUniqueObsUnits(
            ValidationErrors validationErrors,
            Set<String> uniqueStudyAndObsUnit,
            int rowNum,
            ExperimentObservation importRow) {
        String envIdPlusStudyId = createObservationUnitKey(importRow);
        if (uniqueStudyAndObsUnit.contains(envIdPlusStudyId)) {
            String errorMessage = String.format("The ID (%s) is not unique within the environment(%s)", importRow.getExpUnitId(), importRow.getEnv());
            this.addRowError(Columns.EXP_UNIT_ID, errorMessage, validationErrors, rowNum);
        } else {
            uniqueStudyAndObsUnit.add(envIdPlusStudyId);
        }
    }

    private void validateConditionallyRequired(ValidationErrors validationErrors, int rowNum, ExperimentObservation importRow, Program program, boolean commit) {
        ImportObjectState expState = this.trialByNameNoScope.get(importRow.getExpTitle())
                                                            .getState();
        ImportObjectState envState = this.studyByNameNoScope.get(importRow.getEnv()).getState();

        String errorMessage = BLANK_FIELD_EXPERIMENT;
        if (expState == ImportObjectState.EXISTING && envState == ImportObjectState.NEW) {
            errorMessage = BLANK_FIELD_ENV;
        } else if(expState == ImportObjectState.EXISTING && envState == ImportObjectState.EXISTING) {
            errorMessage = BLANK_FIELD_OBS;
        }

        if(expState == ImportObjectState.NEW || envState == ImportObjectState.NEW) {
            validateRequiredCell(importRow.getGid(), Columns.GERMPLASM_GID, errorMessage, validationErrors, rowNum);
            validateRequiredCell(importRow.getExpTitle(),Columns.EXP_TITLE,errorMessage, validationErrors, rowNum);
            validateRequiredCell(importRow.getExpUnit(), Columns.EXP_UNIT, errorMessage, validationErrors, rowNum);
            validateRequiredCell(importRow.getExpType(), Columns.EXP_TYPE, errorMessage, validationErrors, rowNum);
            validateRequiredCell(importRow.getEnv(), Columns.ENV, errorMessage, validationErrors, rowNum);
            if(validateRequiredCell(importRow.getEnvLocation(), Columns.ENV_LOCATION, errorMessage, validationErrors, rowNum)) {
                if(!Utilities.removeProgramKeyAndUnknownAdditionalData(this.studyByNameNoScope.get(importRow.getEnv()).getBrAPIObject().getLocationName(), program.getKey()).equals(importRow.getEnvLocation())) {
                    addRowError(Columns.ENV_LOCATION, ENV_LOCATION_MISMATCH, validationErrors, rowNum);
                }
            }
            if(validateRequiredCell(importRow.getEnvYear(), Columns.ENV_YEAR, errorMessage, validationErrors, rowNum)) {
                String studyYear = this.studyByNameNoScope.get(importRow.getEnv()).getBrAPIObject().getSeasons().get(0);
                String rowYear = importRow.getEnvYear();
                if(commit) {
                    rowYear = this.yearToSeasonDbId(importRow.getEnvYear(), program.getId());
                }
                if(!studyYear.equals(rowYear)) {
                    addRowError(Columns.ENV_YEAR, ENV_YEAR_MISMATCH, validationErrors, rowNum);
                }
            }
            validateRequiredCell(importRow.getExpUnitId(), Columns.EXP_UNIT_ID, errorMessage, validationErrors, rowNum);
            validateRequiredCell(importRow.getExpReplicateNo(), Columns.REP_NUM, errorMessage, validationErrors, rowNum);
            validateRequiredCell(importRow.getExpBlockNo(), Columns.BLOCK_NUM, errorMessage, validationErrors, rowNum);

            if(StringUtils.isNotBlank(importRow.getObsUnitID())) {
                addRowError(Columns.OBS_UNIT_ID, "ObsUnitID cannot be specified when creating a new environment", validationErrors, rowNum);
            }
        } else {
            validateRequiredCell(importRow.getObsUnitID(), Columns.OBS_UNIT_ID, errorMessage, validationErrors, rowNum);
        }
    }

    private boolean validateRequiredCell(String value, String columnHeader, String errorMessage, ValidationErrors validationErrors, int rowNum) {
        if (StringUtils.isBlank(value)) {
            addRowError(columnHeader, errorMessage, validationErrors, rowNum);
            return false;
        }
        return true;
    }

    private void addRowError(String field, String errorMessage, ValidationErrors validationErrors, int rowNum) {
        ValidationError ve = new ValidationError(field, errorMessage, HttpStatus.UNPROCESSABLE_ENTITY);
        validationErrors.addError(rowNum + 2, ve);  // +2 instead of +1 to account for the column header row.
    }

    private void addIfNotNull(HashSet<String> set, String setValue) {
        if (setValue != null) {
            set.add(setValue);
        }
    }

    private Map<String, ImportPreviewStatistics> generateStatisticsMap(List<BrAPIImport> importRows) {
        // Data for stats.
        HashSet<String> environmentNameCounter = new HashSet<>(); // set of unique environment names
        HashSet<String> obsUnitsIDCounter = new HashSet<>(); // set of unique observation unit ID's
        HashSet<String> gidCounter = new HashSet<>(); // set of unique GID's

        for (BrAPIImport row : importRows) {
            ExperimentObservation importRow = (ExperimentObservation) row;
            // Collect date for stats.
            addIfNotNull(environmentNameCounter, importRow.getEnv());
            addIfNotNull(obsUnitsIDCounter, createObservationUnitKey(importRow));
            addIfNotNull(gidCounter, importRow.getGid());
        }

        int numNewObservations = Math.toIntExact(
                observationByHash.values()
                                 .stream()
                                 .filter(preview -> preview != null && preview.getState() == ImportObjectState.NEW &&
                                         !StringUtils.isBlank(preview.getBrAPIObject()
                                                                     .getValue()))
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
                "Environments", environmentStats,
                "Observation_Units", obdUnitStats,
                "GIDs", gidStats,
                "Observations", observationStats
        );
    }

    private void validateGermplasm(ExperimentObservation importRow, ValidationErrors validationErrors, int rowNum, PendingImportObject<BrAPIGermplasm> germplasmPIO) {
        // error if GID is not blank but GID does not already exist
        if (StringUtils.isNotBlank(importRow.getGid()) && germplasmPIO == null) {
            addRowError(Columns.GERMPLASM_GID, "A non-existing GID", validationErrors, rowNum);
        }
    }

    private void validateTestOrCheck(ExperimentObservation importRow, ValidationErrors validationErrors, int rowNum) {
        String testOrCheck = importRow.getTestOrCheck();
        if ( ! ( testOrCheck==null || testOrCheck.isBlank()
                || "C".equalsIgnoreCase(testOrCheck) || "CHECK".equalsIgnoreCase(testOrCheck)
                || "T".equalsIgnoreCase(testOrCheck) || "TEST".equalsIgnoreCase(testOrCheck) )
        ){
            addRowError(Columns.TEST_CHECK, String.format("Invalid value (%s)", testOrCheck), validationErrors, rowNum) ;
        }
    }

    private PendingImportObject<BrAPIGermplasm> getGidPOI(ExperimentObservation importRow) {
        if (this.existingGermplasmByGID.containsKey(importRow.getGid())) {
            return existingGermplasmByGID.get(importRow.getGid());
        }

        return null;
    }

    private PendingImportObject<BrAPIObservationUnit> fetchOrCreateObsUnitPIO(Program program, boolean commit, String envSeqValue, ExperimentObservation importRow) {
        PendingImportObject<BrAPIObservationUnit> pio;
        String key = createObservationUnitKey(importRow);
        if (this.observationUnitByNameNoScope.containsKey(key)) {
            pio = observationUnitByNameNoScope.get(key);
        } else {
            String germplasmName = "";
            if (this.existingGermplasmByGID.get(importRow.getGid()) != null) {
                germplasmName = this.existingGermplasmByGID.get(importRow.getGid())
                                                           .getBrAPIObject()
                                                           .getGermplasmName();
            }
            PendingImportObject<BrAPITrial> trialPIO = this.trialByNameNoScope.get(importRow.getExpTitle());
            UUID trialID = trialPIO.getId();
            UUID datasetId = null;
            if (commit) {
                datasetId = UUID.fromString(trialPIO.getBrAPIObject()
                        .getAdditionalInfo().getAsJsonObject()
                        .get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID).getAsString());
            }
            PendingImportObject<BrAPIStudy> studyPIO = this.studyByNameNoScope.get(importRow.getEnv());
            UUID studyID = studyPIO.getId();
            UUID id = UUID.randomUUID();
            BrAPIObservationUnit newObservationUnit = importRow.constructBrAPIObservationUnit(program, envSeqValue, commit, germplasmName, BRAPI_REFERENCE_SOURCE, trialID, datasetId, studyID, id);
            pio = new PendingImportObject<>(ImportObjectState.NEW, newObservationUnit, id);
            this.observationUnitByNameNoScope.put(key, pio);
        }
        return pio;
    }


    private PendingImportObject<BrAPIObservation> fetchOrCreateObservationPIO(Program program,
                                                                              User user,
                                                                              ExperimentObservation importRow,
                                                                              String variableName,
                                                                              String value,
                                                                              String timeStampValue,
                                                                              boolean commit,
                                                                              String seasonDbId,
                                                                              PendingImportObject<BrAPIObservationUnit> obsUnitPIO) {
        PendingImportObject<BrAPIObservation> pio;
        String key = getImportObservationHash(importRow, variableName);
        if (this.observationByHash.containsKey(key)) {
            pio = observationByHash.get(key);
        } else {
            PendingImportObject<BrAPITrial> trialPIO = this.trialByNameNoScope.get(importRow.getExpTitle());
            UUID trialID = trialPIO.getId();
            PendingImportObject<BrAPIStudy> studyPIO = this.studyByNameNoScope.get(importRow.getEnv());
            UUID studyID = studyPIO.getId();
            UUID id = UUID.randomUUID();
            BrAPIObservation newObservation = importRow.constructBrAPIObservation(value, variableName, seasonDbId, obsUnitPIO.getBrAPIObject(), commit, program, user, BRAPI_REFERENCE_SOURCE, trialID, studyID, obsUnitPIO.getId(), id);
            //NOTE: Can't parse invalid timestamp value, so have to skip if invalid.
            // Validation error should be thrown for offending value, but that doesn't happen until later downstream
            if (timeStampValue != null && !timeStampValue.isBlank() && (validDateValue(timeStampValue) || validDateTimeValue(timeStampValue))) {
                newObservation.setObservationTimeStamp(OffsetDateTime.parse(timeStampValue));
            }

            newObservation.setStudyDbId(this.studyByNameNoScope.get(importRow.getEnv()).getId().toString()); //set as the BI ID to facilitate looking up studies when saving new observations

            pio = new PendingImportObject<>(ImportObjectState.NEW, newObservation);
            this.observationByHash.put(key, pio);
        }
        return pio;
    }
    private void addObsVarsToDatasetDetails(PendingImportObject<BrAPIListDetails> pio, List<Trait> referencedTraits, Program program) {
        BrAPIListDetails details = pio.getBrAPIObject();
        referencedTraits.forEach(trait -> {
            String id = Utilities.appendProgramKey(trait.getObservationVariableName(), program.getKey());

            // Don't append the key if connected to a brapi service operating with legacy data(no appended program key)
            if (trait.getFullName() == null) {
                id = trait.getObservationVariableName();
            }
            
            if (!details.getData().contains(id) && ImportObjectState.EXISTING != pio.getState()) {
                details.getData().add(id);
            }
            if (!details.getData().contains(id) && ImportObjectState.EXISTING == pio.getState()) {
                details.getData().add(id);
                pio.setState(ImportObjectState.MUTATED);
            }
        });
    }
    private PendingImportObject<BrAPIListDetails> fetchOrCreateDatasetPIO(ExperimentObservation importRow, Program program, List<Trait> referencedTraits) {
        PendingImportObject<BrAPIListDetails> pio;
        PendingImportObject<BrAPITrial> trialPIO = trialByNameNoScope.get(importRow.getExpTitle());
        String name = String.format("Observation Dataset [%s-%s]",
                program.getKey(),
                trialPIO.getBrAPIObject()
                        .getAdditionalInfo()
                        .get(BrAPIAdditionalInfoFields.EXPERIMENT_NUMBER)
                        .getAsString());
        if (obsVarDatasetByName.containsKey(name)) {
            pio = obsVarDatasetByName.get(name);
        } else {
            UUID id = UUID.randomUUID();
            BrAPIListDetails newDataset = importRow.constructDatasetDetails(
                    name,
                    id,
                    BRAPI_REFERENCE_SOURCE,
                    program,
                    trialPIO.getId().toString());
            pio = new PendingImportObject<BrAPIListDetails>(ImportObjectState.NEW, newDataset, id);
            trialPIO.getBrAPIObject().putAdditionalInfoItem("observationDatasetId", id.toString());
            if (ImportObjectState.EXISTING == trialPIO.getState()) {
                trialPIO.setState(ImportObjectState.MUTATED);
            }
            obsVarDatasetByName.put(name, pio);
        }
        addObsVarsToDatasetDetails(pio, referencedTraits, program);
        return pio;
    }

    private PendingImportObject<BrAPIStudy> fetchOrCreateStudyPIO(Program program, boolean commit, String expSequenceValue, ExperimentObservation importRow, Supplier<BigInteger> envNextVal) {
        PendingImportObject<BrAPIStudy> pio;
        if (studyByNameNoScope.containsKey(importRow.getEnv())) {
            pio = studyByNameNoScope.get(importRow.getEnv());
        } else {
            PendingImportObject<BrAPITrial> trialPIO = this.trialByNameNoScope.get(importRow.getExpTitle());
            UUID trialID = trialPIO.getId();
            UUID id = UUID.randomUUID();
            BrAPIStudy newStudy = importRow.constructBrAPIStudy(program, commit, BRAPI_REFERENCE_SOURCE, expSequenceValue, trialID, id, envNextVal);
            newStudy.setLocationDbId(this.locationByName.get(importRow.getEnvLocation()).getId().toString()); //set as the BI ID to facilitate looking up locations when saving new studies

            if (commit) {
                String year = newStudy.getSeasons().get(0); // It is assumed that the study has only one season
                if(StringUtils.isNotBlank(year)) {
                    String seasonID = this.yearToSeasonDbId(year, program.getId());
                    newStudy.setSeasons(Collections.singletonList(seasonID));
                }
            }

            pio = new PendingImportObject<>(ImportObjectState.NEW, newStudy, id);
            this.studyByNameNoScope.put(importRow.getEnv(), pio);
        }
        return pio;
    }

    private PendingImportObject<ProgramLocation> fetchOrCreateLocationPIO(ExperimentObservation importRow) {
        PendingImportObject<ProgramLocation> pio;
        if (locationByName.containsKey((importRow.getEnvLocation()))) {
            pio = locationByName.get(importRow.getEnvLocation());
        } else {
            ProgramLocation newLocation = importRow.constructProgramLocation();
            pio = new PendingImportObject<>(ImportObjectState.NEW, newLocation, UUID.randomUUID());
            this.locationByName.put(importRow.getEnvLocation(), pio);
        }
        return pio;
    }

    private PendingImportObject<BrAPITrial> fetchOrCreateTrialPIO(Program program, User user, boolean commit, ExperimentObservation importRow, Supplier<BigInteger> expNextVal) {
        PendingImportObject<BrAPITrial> pio;
        if (trialByNameNoScope.containsKey(importRow.getExpTitle())) {
            pio = trialByNameNoScope.get(importRow.getExpTitle());
        } else {
            UUID id = UUID.randomUUID();
            String expSeqValue = null;
            if (commit) {
                expSeqValue = expNextVal.get().toString();
            }
            BrAPITrial newTrial = importRow.constructBrAPITrial(program, user, commit, BRAPI_REFERENCE_SOURCE, id, expSeqValue);
            pio = new PendingImportObject<>(ImportObjectState.NEW, newTrial, id);
            this.trialByNameNoScope.put(importRow.getExpTitle(), pio);
        }
        return pio;
    }

    private void updateObservationDependencyValues(Program program) {
        String programKey = program.getKey();

        // update the observations study DbIds, Observation Unit DbIds and Germplasm DbIds
        this.observationUnitByNameNoScope.values().stream()
                                         .map(PendingImportObject::getBrAPIObject)
                                         .forEach(obsUnit -> updateObservationDbIds(obsUnit, programKey));

        // Update ObservationVariable DbIds
        List<Trait> traits = getTraitList(program);
        Map<String, Trait> traitMap = traits.stream().collect(Collectors.toMap(TraitEntity::getObservationVariableName, Function.identity()));

        for (PendingImportObject<BrAPIObservation> observation : this.observationByHash.values()) {
            String observationVariableName = observation.getBrAPIObject().getObservationVariableName();
            if (observationVariableName != null && traitMap.containsKey(observationVariableName)) {
                String observationVariableDbId = traitMap.get(observationVariableName).getObservationVariableDbId();
                observation.getBrAPIObject().setObservationVariableDbId(observationVariableDbId);
            }
        }
    }

    private List<Trait> getTraitList(Program program) {
        try {
            return ontologyService.getTraitsByProgramId(program.getId(), true);
        } catch (DoesNotExistException e) {
            log.error(e.getMessage(), e);
            throw new InternalServerException(e.toString(), e);
        }
    }

    // Update each ovservation's observationUnit DbId, study DbId, and germplasm DbId
    private void updateObservationDbIds(BrAPIObservationUnit obsUnit, String programKey) {
        // FILTER LOGIC: Match on Env and Exp Unit ID
        this.observationByHash.values()
                              .stream()
                              .filter(obs -> obs.getBrAPIObject()
                                                .getAdditionalInfo() != null
                                                      && obs.getBrAPIObject()
                                                            .getAdditionalInfo()
                                                            .get(BrAPIAdditionalInfoFields.STUDY_NAME) != null
                                                      && obs.getBrAPIObject()
                                                            .getAdditionalInfo()
                                                            .get(BrAPIAdditionalInfoFields.STUDY_NAME)
                                                            .getAsString()
                                                            .equals(Utilities.removeProgramKeyAndUnknownAdditionalData(obsUnit.getStudyName(), programKey))
                                                      && Utilities.removeProgramKeyAndUnknownAdditionalData(obs.getBrAPIObject().getObservationUnitName(), programKey)
                                                                  .equals(Utilities.removeProgramKeyAndUnknownAdditionalData(obsUnit.getObservationUnitName(), programKey))
                              )
                              .forEach(obs -> {
                                  if (StringUtils.isBlank(obs.getBrAPIObject().getObservationUnitDbId())) {
                                      obs.getBrAPIObject().setObservationUnitDbId(obsUnit.getObservationUnitDbId());
                                  }
                                  obs.getBrAPIObject().setStudyDbId(obsUnit.getStudyDbId());
                                  obs.getBrAPIObject().setGermplasmDbId(obsUnit.getGermplasmDbId());
                              });
    }

    private void updateObsUnitDependencyValues(String programKey) {

        // update study DbIds
        this.studyByNameNoScope.values()
                               .stream()
                               .filter(Objects::nonNull)
                               .distinct()
                               .map(PendingImportObject::getBrAPIObject)
                               .forEach(study -> updateStudyDbId(study, programKey));

        // update germplasm DbIds
        this.existingGermplasmByGID.values()
                                   .stream()
                                   .filter(Objects::nonNull)
                                   .distinct()
                                   .map(PendingImportObject::getBrAPIObject)
                                   .forEach(this::updateGermplasmDbId);
    }

    private void updateStudyDbId(BrAPIStudy study, String programKey) {
        this.observationUnitByNameNoScope.values()
                                         .stream()
                                         .filter(obsUnit -> obsUnit.getBrAPIObject()
                                                                   .getStudyName()
                                                                   .equals(Utilities.removeProgramKeyAndUnknownAdditionalData(study.getStudyName(), programKey)))
                                         .forEach(obsUnit -> {
                                             obsUnit.getBrAPIObject()
                                                    .setStudyDbId(study.getStudyDbId());
                                             obsUnit.getBrAPIObject()
                                                    .setTrialDbId(study.getTrialDbId());
                                         });
    }

    private void updateGermplasmDbId(BrAPIGermplasm germplasm) {
        this.observationUnitByNameNoScope.values()
                                         .stream()
                                         .filter(obsUnit -> obsUnit.getBrAPIObject()
                                                                   .getGermplasmName() != null &&
                                                 obsUnit.getBrAPIObject()
                                                        .getGermplasmName()
                                                        .equals(germplasm.getGermplasmName()))
                                         .forEach(obsUnit -> obsUnit.getBrAPIObject()
                                                                    .setGermplasmDbId(germplasm.getGermplasmDbId()));
    }


    private void updateStudyDependencyValues(Map<Integer, PendingImport> mappedBrAPIImport, String programKey) {
        // update location DbIds in studies for all distinct locations
        mappedBrAPIImport.values()
                         .stream()
                         .map(PendingImport::getLocation)
                         .forEach(this::updateStudyLocationDbId);

        // update trial DbIds in studies for all distinct trials
        this.trialByNameNoScope.values()
                               .stream()
                               .filter(Objects::nonNull)
                               .distinct()
                               .map(PendingImportObject::getBrAPIObject)
                               .forEach(trial -> this.updateTrialDbId(trial, programKey));
    }

    private void updateStudyLocationDbId(PendingImportObject<ProgramLocation> location) {
        this.studyByNameNoScope.values()
                               .stream()
                               .filter(study -> location.getId().toString()
                                                        .equals(study.getBrAPIObject()
                                                                     .getLocationDbId()))
                               .forEach(study -> study.getBrAPIObject()
                                                      .setLocationDbId(location.getBrAPIObject().getLocationDbId()));
    }

    private void updateTrialDbId(BrAPITrial trial, String programKey) {
        this.studyByNameNoScope.values()
                               .stream()
                               .filter(study -> study.getBrAPIObject()
                                                     .getTrialName()
                                                     .equals(Utilities.removeProgramKey(trial.getTrialName(), programKey)))
                               .forEach(study -> study.getBrAPIObject()
                                                      .setTrialDbId(trial.getTrialDbId()));
    }

    private ArrayList<BrAPIGermplasm> getGermplasmByAccessionNumber(
            List<String> germplasmAccessionNumbers,
            UUID programId) throws ApiException {
        List<BrAPIGermplasm> germplasmList = brAPIGermplasmDAO.getGermplasm(programId);
        ArrayList<BrAPIGermplasm> resultGermplasm = new ArrayList<>();
        // Search for accession number matches
        for (BrAPIGermplasm germplasm : germplasmList) {
            for (String accessionNumber : germplasmAccessionNumbers) {
                if (germplasm.getAccessionNumber()
                             .equals(accessionNumber)) {
                    resultGermplasm.add(germplasm);
                    break;
                }
            }
        }
        return resultGermplasm;
    }

    private Map<String, PendingImportObject<BrAPIObservationUnit>> initializeObservationUnits(Program program, List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPIObservationUnit>> ret = new HashMap<>();

        Map<String, ExperimentObservation> rowByObsUnitId = new HashMap<>();
        experimentImportRows.forEach(row -> {
            if (StringUtils.isNotBlank(row.getObsUnitID())) {
                if(rowByObsUnitId.containsKey(row.getObsUnitID())) {
                    throw new IllegalStateException("ObsUnitId is repeated: " + row.getObsUnitID());
                }
                rowByObsUnitId.put(row.getObsUnitID(), row);
            }
        });

        try {
            List<BrAPIObservationUnit> existingObsUnits = brAPIObservationUnitDAO.getObservationUnitsById(rowByObsUnitId.keySet(), program);

            String refSource = String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName());
            if (existingObsUnits.size() == rowByObsUnitId.size()) {
                existingObsUnits.forEach(brAPIObservationUnit -> {
                    processAndCacheObservationUnit(brAPIObservationUnit, refSource, program, ret, rowByObsUnitId);

                    BrAPIExternalReference idRef = Utilities.getExternalReference(brAPIObservationUnit.getExternalReferences(), refSource)
                                                            .orElseThrow(() -> new InternalServerException("An ObservationUnit ID was not found in any of the external references"));

                    ExperimentObservation row = rowByObsUnitId.get(idRef.getReferenceID());
                    row.setExpTitle(Utilities.removeProgramKey(brAPIObservationUnit.getTrialName(), program.getKey()));
                    row.setEnv(Utilities.removeProgramKeyAndUnknownAdditionalData(brAPIObservationUnit.getStudyName(), program.getKey()));
                    row.setEnvLocation(Utilities.removeProgramKey(brAPIObservationUnit.getLocationName(), program.getKey()));
                });
            } else {
                List<String> missingIds = new ArrayList<>(rowByObsUnitId.keySet());
                missingIds.removeAll(existingObsUnits.stream().map(BrAPIObservationUnit::getObservationUnitDbId).collect(Collectors.toList()));
                throw new IllegalStateException("Observation Units not found for ObsUnitId(s): " + String.join(COMMA_DELIMITER, missingIds));
            }

            return ret;
        } catch (ApiException e) {
            log.error("Error fetching observation units: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        }
    }

    private Map<String, PendingImportObject<BrAPITrial>> initializeTrialByNameNoScope(Program program, List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPITrial>> trialByName = new HashMap<>();
        String programKey = program.getKey();

        initializeTrialsForExistingObservationUnits(program, trialByName);

        List<String> uniqueTrialNames = experimentImportRows.stream()
                                                            .filter(row -> StringUtils.isBlank(row.getObsUnitID()))
                                                            .map(ExperimentObservation::getExpTitle)
                                                            .distinct()
                                                            .collect(Collectors.toList());
        try {
            String trialRefSource = String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName());
            brapiTrialDAO.getTrialsByName(uniqueTrialNames, program)
                         .forEach(existingTrial -> processAndCacheTrial(existingTrial, program, trialRefSource, trialByName));
        } catch (ApiException e) {
            log.error("Error fetching trials: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        }

        return trialByName;
    }

    private Map<String, PendingImportObject<BrAPIStudy>> initializeStudyByNameNoScope(Program program, List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPIStudy>> studyByName = new HashMap<>();
        if (this.trialByNameNoScope.size() != 1) {
            return studyByName;
        }

        try {
            initializeStudiesForExistingObservationUnits(program, studyByName);
        } catch (ApiException e) {
            log.error("Error fetching studies: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        }

        List<BrAPIStudy> existingStudies;
        Optional<PendingImportObject<BrAPITrial>> trial = getTrialPIO(experimentImportRows);

        try {
            if (trial.isEmpty()) {
                // TODO: throw ValidatorException and return 422
            }
            UUID experimentId = trial.get().getId();
            existingStudies = brAPIStudyDAO.getStudiesByExperimentID(experimentId, program);
            existingStudies.forEach(existingStudy -> processAndCacheStudy(existingStudy, program, studyByName));
        } catch (ApiException e) {
            log.error("Error fetching studies: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        }

        return studyByName;
    }

    private void initializeStudiesForExistingObservationUnits(Program program, Map<String, PendingImportObject<BrAPIStudy>> studyByName) throws ApiException {
        Set<String> studyDbIds = this.observationUnitByNameNoScope.values()
                                                               .stream()
                                                               .map(pio -> pio.getBrAPIObject()
                                                                              .getStudyDbId())
                                                               .collect(Collectors.toSet());

        List<BrAPIStudy> studies = fetchStudiesByDbId(studyDbIds, program);
        studies.forEach(study -> {
            processAndCacheStudy(study, program, studyByName);
        });
    }

    private List<BrAPIStudy> fetchStudiesByDbId(Set<String> studyDbIds, Program program) throws ApiException {
        List<BrAPIStudy> studies = brAPIStudyDAO.getStudiesByStudyDbId(studyDbIds, program);
        if(studies.size() != studyDbIds.size()) {
            List<String> missingIds = new ArrayList<>(studyDbIds);
            missingIds.removeAll(studies.stream().map(BrAPIStudy::getStudyDbId).collect(Collectors.toList()));
            throw new IllegalStateException("Study not found for studyDbId(s): " + String.join(COMMA_DELIMITER, missingIds));
        }
        return studies;
    }

    private Map<String, PendingImportObject<ProgramLocation>> initializeUniqueLocationNames(Program program, List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<ProgramLocation>> locationByName = new HashMap<>();

        List<ProgramLocation> existingLocations = new ArrayList<>();
        if(studyByNameNoScope.size() > 0) {
            Set<String> locationDbIds = studyByNameNoScope.values()
                                                          .stream()
                                                          .map(study -> study.getBrAPIObject()
                                                                             .getLocationDbId())
                                                          .collect(Collectors.toSet());
            try {
                existingLocations.addAll(locationService.getLocationsByDbId(locationDbIds, program.getId()));
            } catch (ApiException e) {
                log.error("Error fetching locations: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            }
        }

        List<String> uniqueLocationNames = experimentImportRows.stream()
                                                               .filter(experimentObservation -> StringUtils.isBlank(experimentObservation.getObsUnitID()))
                                                               .map(ExperimentObservation::getEnvLocation)
                                                               .distinct()
                                                               .filter(Objects::nonNull)
                                                               .collect(Collectors.toList());

        try {
            existingLocations.addAll(locationService.getLocationsByName(uniqueLocationNames, program.getId()));
        } catch (ApiException e) {
            log.error("Error fetching locations: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        }

        existingLocations.forEach(existingLocation -> locationByName.put(existingLocation.getName(), new PendingImportObject<>(ImportObjectState.EXISTING, existingLocation, existingLocation.getId())));
        return locationByName;
    }
    private Map<String, PendingImportObject<BrAPIListDetails>> initializeObsVarDatasetByName(Program program, List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName = new HashMap<>();

        Optional<PendingImportObject<BrAPITrial>> trialPIO = getTrialPIO(experimentImportRows);

        if (trialPIO.isPresent() && trialPIO.get().getBrAPIObject().getAdditionalInfo().has(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID)) {
            String datasetId = trialPIO.get().getBrAPIObject()
                    .getAdditionalInfo()
                    .get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID)
                    .getAsString();
          try {
            List<BrAPIListSummary> existingDatasets = brAPIListDAO
                    .getListByTypeAndExternalRef(BrAPIListTypes.OBSERVATIONVARIABLES,
                    program.getId(),
                    String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.DATASET.getName()),
                    UUID.fromString(datasetId));
            if (existingDatasets == null || existingDatasets.isEmpty()) {
              throw new InternalServerException("existing dataset summary not returned from brapi server");
            }
            BrAPIListDetails dataSetDetails = brAPIListDAO
                    .getListById(existingDatasets.get(0).getListDbId(), program.getId())
                    .getResult();
            processAndCacheObsVarDataset(dataSetDetails, program, obsVarDatasetByName);
          } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
          }
        }
        return obsVarDatasetByName;
    }

    private Optional<PendingImportObject<BrAPITrial>> getTrialPIO(List<ExperimentObservation> experimentImportRows) {
        Optional<String> expTitle = experimentImportRows.stream()
                .filter(row -> StringUtils.isBlank(row.getObsUnitID()) && StringUtils.isNotBlank(row.getExpTitle()))
                .map(ExperimentObservation::getExpTitle)
                .findFirst();

        if (expTitle.isEmpty() && trialByNameNoScope.keySet().stream().findFirst().isEmpty()) {
            return Optional.empty();
        }
        if(expTitle.isEmpty()) {
            expTitle = trialByNameNoScope.keySet().stream().findFirst();
        }

        return Optional.ofNullable(this.trialByNameNoScope.get(expTitle.get()));
    }
    private void processAndCacheObsVarDataset(BrAPIListDetails existingList, Program program, Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName) {
        BrAPIExternalReference xref = Utilities.getExternalReference(existingList.getExternalReferences(),
                        String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.DATASET.getName()))
                .orElseThrow(() -> new IllegalStateException("External references wasn't found for list (dbid): " + existingList.getListDbId()));
        obsVarDatasetByName.put(existingList.getListName(),
                new PendingImportObject<BrAPIListDetails>(ImportObjectState.EXISTING, existingList, UUID.fromString(xref.getReferenceID())));
    }
    private Map<String, PendingImportObject<BrAPIGermplasm>> initializeExistingGermplasmByGID(Program program, List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = new HashMap<>();

        List<BrAPIGermplasm> existingGermplasms = new ArrayList<>();
        if(observationUnitByNameNoScope.size() > 0) {
            Set<String> germplasmDbIds = observationUnitByNameNoScope.values().stream().map(ou -> ou.getBrAPIObject().getGermplasmDbId()).collect(Collectors.toSet());
            try {
                existingGermplasms.addAll(brAPIGermplasmDAO.getGermplasmsByDBID(germplasmDbIds, program.getId()));
            } catch (ApiException e) {
                log.error("Error fetching germplasm: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            }
        }

        List<String> uniqueGermplasmGIDs = experimentImportRows.stream()
                                                               .filter(experimentObservation -> StringUtils.isBlank(experimentObservation.getObsUnitID()))
                                                               .map(ExperimentObservation::getGid)
                                                               .distinct()
                                                               .collect(Collectors.toList());

        try {
            existingGermplasms.addAll(this.getGermplasmByAccessionNumber(uniqueGermplasmGIDs, program.getId()));
        } catch (ApiException e) {
            log.error("Error fetching germplasm: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        }

        existingGermplasms.forEach(existingGermplasm -> {
            BrAPIExternalReference xref = Utilities.getExternalReference(existingGermplasm.getExternalReferences(), String.format("%s", BRAPI_REFERENCE_SOURCE))
                                                   .orElseThrow(() -> new IllegalStateException("External references wasn't found for germplasm (dbid): " + existingGermplasm.getGermplasmDbId()));
            existingGermplasmByGID.put(existingGermplasm.getAccessionNumber(), new PendingImportObject<>(ImportObjectState.EXISTING, existingGermplasm, UUID.fromString(xref.getReferenceID())));
        });
        return existingGermplasmByGID;
    }

    private void processAndCacheObservationUnit(BrAPIObservationUnit brAPIObservationUnit, String refSource, Program program, Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByName,
                                                Map<String, ExperimentObservation> rowByObsUnitId) {
        BrAPIExternalReference idRef = Utilities.getExternalReference(brAPIObservationUnit.getExternalReferences(), refSource)
                                                .orElseThrow(() -> new InternalServerException("An ObservationUnit ID was not found in any of the external references"));

        ExperimentObservation row = rowByObsUnitId.get(idRef.getReferenceID());
        row.setExpUnitId(Utilities.removeProgramKeyAndUnknownAdditionalData(brAPIObservationUnit.getObservationUnitName(), program.getKey()));
        observationUnitByName.put(createObservationUnitKey(row),
                new PendingImportObject<>(ImportObjectState.EXISTING,
                                          brAPIObservationUnit,
                                          UUID.fromString(idRef.getReferenceID())));
    }

    private void processAndCacheStudy(BrAPIStudy existingStudy, Program program, Map<String, PendingImportObject<BrAPIStudy>> studyByName) {
        BrAPIExternalReference xref = Utilities.getExternalReference(existingStudy.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.STUDIES.getName()))
                                               .orElseThrow(() -> new IllegalStateException("External references wasn't found for study (dbid): " + existingStudy.getStudyDbId()));
        studyByName.put(
                Utilities.removeProgramKeyAndUnknownAdditionalData(existingStudy.getStudyName(), program.getKey()),
                new PendingImportObject<>(ImportObjectState.EXISTING, existingStudy, UUID.fromString(xref.getReferenceID())));
    }

    private void initializeTrialsForExistingObservationUnits(Program program, Map<String, PendingImportObject<BrAPITrial>> trialByName) {
        if(observationUnitByNameNoScope.size() > 0) {
            Set<String> trialDbIds = new HashSet<>();
            Set<String> studyDbIds = new HashSet<>();

            observationUnitByNameNoScope.values()
                                        .forEach(pio -> {
                                            BrAPIObservationUnit existingOu = pio.getBrAPIObject();
                                            if (StringUtils.isBlank(existingOu.getTrialDbId()) && StringUtils.isBlank(existingOu.getStudyDbId())) {
                                                throw new IllegalStateException("TrialDbId and StudyDbId are not set for an existing ObservationUnit");
                                            }

                                            if (StringUtils.isNotBlank(existingOu.getTrialDbId())) {
                                                trialDbIds.add(existingOu.getTrialDbId());
                                            } else {
                                                studyDbIds.add(existingOu.getStudyDbId());
                                            }
                                        });

            //if the OU doesn't have the trialDbId set, then fetch the study to fetch the trialDbId
            if(!studyDbIds.isEmpty()) {
                try {
                    trialDbIds.addAll(fetchTrialDbidsForStudies(studyDbIds, program));
                } catch (ApiException e) {
                    log.error("Error fetching studies: " + Utilities.generateApiExceptionLogMessage(e), e);
                    throw new InternalServerException(e.toString(), e);
                }
            }

            try {
                List<BrAPITrial> trials = brapiTrialDAO.getTrialsByDbIds(trialDbIds, program);
                if (trials.size() != trialDbIds.size()) {
                    List<String> missingIds = new ArrayList<>(trialDbIds);
                    missingIds.removeAll(trials.stream().map(BrAPITrial::getTrialDbId).collect(Collectors.toList()));
                    throw new IllegalStateException("Trial not found for trialDbId(s): " + String.join(COMMA_DELIMITER, missingIds));
                }

                String trialRefSource = String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName());
                trials.forEach(trial -> processAndCacheTrial(trial, program, trialRefSource, trialByName));
            } catch (ApiException e) {
                log.error("Error fetching trials: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            }
        }
    }

    private Set<String> fetchTrialDbidsForStudies(Set<String> studyDbIds, Program program) throws ApiException {
        Set<String> trialDbIds = new HashSet<>();
        List<BrAPIStudy> studies = fetchStudiesByDbId(studyDbIds, program);
        studies.forEach(study -> {
            if (StringUtils.isBlank(study.getTrialDbId())) {
                throw new IllegalStateException("TrialDbId is not set for an existing Study: " + study.getStudyDbId());
            }
            trialDbIds.add(study.getTrialDbId());
        });

        return trialDbIds;
    }

    private void processAndCacheTrial(BrAPITrial existingTrial, Program program, String trialRefSource, Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope) {

        //get TrialId from existingTrial
        BrAPIExternalReference experimentIDRef = Utilities.getExternalReference(existingTrial.getExternalReferences(), trialRefSource)
                                                          .orElseThrow(() -> new InternalServerException("An Experiment ID was not found in any of the external references"));
        UUID experimentId = UUID.fromString(experimentIDRef.getReferenceID());

        trialByNameNoScope.put(
                Utilities.removeProgramKey(existingTrial.getTrialName(), program.getKey()),
                new PendingImportObject<>(ImportObjectState.EXISTING, existingTrial, experimentId));
    }

    private void validateTimeStampValue(String value,
                                        String columnHeader, ValidationErrors validationErrors, int row) {
        if (StringUtils.isBlank(value)) {
            log.debug(String.format("skipping validation of observation timestamp because there is no value.\n\tvariable: %s\n\trow: %d", columnHeader, row));
            return;
        }
        if (!validDateValue(value) && !validDateTimeValue(value)) {
            addRowError(columnHeader, "Incorrect datetime format detected. Expected YYYY-MM-DD or YYYY-MM-DDThh:mm:ss+hh:mm", validationErrors, row);
        }

    }

    private void validateObservationValue(Trait variable, String value,
                                          String columnHeader, ValidationErrors validationErrors, int row) {
        if (StringUtils.isBlank(value)) {
            log.debug(String.format("skipping validation of observation because there is no value.\n\tvariable: %s\n\trow: %d", variable.getObservationVariableName(), row));
            return;
        }

        switch (variable.getScale().getDataType()) {
            case NUMERICAL:
                Optional<BigDecimal> number = validNumericValue(value);
                if (number.isEmpty()) {
                    addRowError(columnHeader, "Non-numeric text detected detected", validationErrors, row);
                } else if (!validNumericRange(number.get(), variable.getScale())) {
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
        Set<String> categoryValues = categories.stream()
                                               .map(category -> category.getValue().toLowerCase())
                                               .collect(Collectors.toSet());
        return categoryValues.contains(value.toLowerCase());
    }

    /**
     * Converts year String to SeasonDbId
     * <br>
     * NOTE: This assumes that the only Season records of interest are ones
     * with a blank name or a name that is the same as the year.
     *
     * @param year      The year as a string
     * @param programId the program ID.
     * @return the DbId of the season-record associated with the year
     */
    private String yearToSeasonDbId(String year, UUID programId) {
        String dbID = null;
        if (this.yearToSeasonDbIdCache.containsKey(year)) { // get it from cache if possible
            dbID = this.yearToSeasonDbIdCache.get(year);
        } else {
            dbID = this.yearToSeasonDbIdFromDatabase(year, programId);
            this.yearToSeasonDbIdCache.put(year, dbID);
        }
        return dbID;
    }

    private String seasonDbIdToYear(String seasonDbId, UUID programId) {
        String year = null;
        if (this.seasonDbIdToYearCache.containsKey(seasonDbId)) { // get it from cache if possible
            year = this.seasonDbIdToYearCache.get(seasonDbId);
        } else {
            year = this.seasonDbIdToYearFromDatabase(seasonDbId, programId);
            this.seasonDbIdToYearCache.put(seasonDbId, year);
        }
        return year;
    }

    private String yearToSeasonDbIdFromDatabase(String year, UUID programId) {
        BrAPISeason targetSeason = null;
        List<BrAPISeason> seasons;
        try {
            seasons = this.brAPISeasonDAO.getSeasonsByYear(year, programId);
            for (BrAPISeason season : seasons) {
                if (null == season.getSeasonName() || season.getSeasonName().isBlank() || season.getSeasonName().equals(year)) {
                    targetSeason = season;
                    break;
                }
            }
            if (targetSeason == null) {
                BrAPISeason newSeason = new BrAPISeason();
                newSeason.setYear(Integer.parseInt(year));
                newSeason.setSeasonName(year);
                targetSeason = this.brAPISeasonDAO.addOneSeason(newSeason, programId);
            }

        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            log.error(e.getResponseBody(), e);
        }

        return (targetSeason == null) ? null : targetSeason.getSeasonDbId();
    }

    private String seasonDbIdToYearFromDatabase(String seasonDbId, UUID programId) {
        BrAPISeason season = null;
        try {
            season = this.brAPISeasonDAO.getSeasonById(seasonDbId, programId);
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
        }
        Integer yearInt = (season == null) ? null : season.getYear();
        return (yearInt == null) ? "" : yearInt.toString();
    }
}
