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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.reactivex.functions.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.JSON;
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
import org.breedinginsight.brapi.v2.dao.*;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.ChangeLogEntry;
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
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Prototype
public class ExperimentProcessor implements Processor {

    private static final String NAME = "Experiment";
    private static final String MISSING_OBS_UNIT_ID_ERROR = "Experimental entities are missing ObsUnitIDs. Import cannot proceed";
    private static final String PREEXISTING_EXPERIMENT_TITLE = "Experiment Title already exists";
    private static final String MULTIPLE_EXP_TITLES = "File contains more than one Experiment Title";
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
    // then updated by the initNewBrapiData() method.
    private Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope = new HashMap<>();
    private Map<String, PendingImportObject<BrAPITrial>> pendingTrialByOUId = new HashMap<>();
    private Map<String, PendingImportObject<ProgramLocation>> locationByName = null;
    private Map<String, PendingImportObject<ProgramLocation>> pendingLocationByOUId = new HashMap<>();
    private Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope = new HashMap<>();
    private Map<String, PendingImportObject<BrAPIStudy>> pendingStudyByOUId = new HashMap<>();
    private Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName = null;
    private Map<String, PendingImportObject<BrAPIListDetails>> pendingObsDatasetByOUId = new HashMap<>();
    //  It is assumed that there are no preexisting Observation Units for the given environment (so this will not be
    // initialized by getExistingBrapiData() )
    private Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope = null;
    private Map<String, PendingImportObject<BrAPIObservationUnit>> pendingObsUnitByOUId = new HashMap<>();

    private final Map<String, PendingImportObject<BrAPIObservation>> observationByHash = new HashMap<>();
    private Map<String, BrAPIObservation> existingObsByObsHash = new HashMap<>();
    // existingGermplasmByGID is populated by getExistingBrapiData(), but not updated by the initNewBrapiData() method
    private Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = new HashMap<>();
    private Map<String, PendingImportObject<BrAPIGermplasm>> pendingGermplasmByOUId = new HashMap<>();

    // Associates timestamp columns to associated phenotype column name for ease of storage
    private final Map<String, Column<?>> timeStampColByPheno = new HashMap<>();
    private final Gson gson;
    private boolean hasAllReferenceUnitIds = true;
    private boolean hasNoReferenceUnitIds = true;
    private Set<String> referenceOUIds = new HashSet<>();

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
        this.gson = new JSON().getGson();
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

        // check for references to Deltabreed-generated observation units
        referenceOUIds = collateReferenceOUIds(importRows);

        if (hasAllReferenceUnitIds) {
            try {

                // get all prior units referenced in import
                pendingObsUnitByOUId = fetchReferenceObservationUnits(referenceOUIds, program);
                observationUnitByNameNoScope = mapPendingObservationUnitByName(pendingObsUnitByOUId, program);
                initializeTrialsForExistingObservationUnits(program, trialByNameNoScope);
                initializeStudiesForExistingObservationUnits(program, studyByNameNoScope);
                locationByName = initializeLocationByName(program, studyByNameNoScope);
                obsVarDatasetByName = initializeObsVarDatasetForExistingObservationUnits(trialByNameNoScope, program);
                existingGermplasmByGID = initializeGermplasmByGIDForExistingObservationUnits(observationUnitByNameNoScope, program);
                for (Map.Entry<String, PendingImportObject<BrAPIObservationUnit>>  unitEntry : pendingObsUnitByOUId.entrySet()) {
                    String unitId = unitEntry.getKey();
                    BrAPIObservationUnit unit = unitEntry.getValue().getBrAPIObject();
                    mapPendingTrialByOUId(unitId, unit, trialByNameNoScope, studyByNameNoScope, pendingTrialByOUId, program);
                    mapPendingStudyByOUId(unitId, unit, studyByNameNoScope, pendingStudyByOUId, program);
                    mapPendingLocationByOUId(unitId, unit, pendingStudyByOUId, locationByName, pendingLocationByOUId);
                    mapPendingObsDatasetByOUId(unitId, pendingTrialByOUId, obsVarDatasetByName, pendingObsDatasetByOUId);
                    mapGermplasmByOUId(unitId, unit, existingGermplasmByGID, pendingGermplasmByOUId);
                }

            } catch (ApiException e) {
                log.error("Error fetching observation units: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            } catch (Exception e) {
                log.error("Error processing experiment with ", e);
                throw new InternalServerException(e.toString(), e);
            }
        } else if (hasNoReferenceUnitIds) {
            observationUnitByNameNoScope = initializeObservationUnits(program, experimentImportRows);
            trialByNameNoScope = initializeTrialByNameNoScope(program, experimentImportRows);
            studyByNameNoScope = initializeStudyByNameNoScope(program, experimentImportRows);
            locationByName = initializeUniqueLocationNames(program, experimentImportRows);
            obsVarDatasetByName = initializeObsVarDatasetByName(program, experimentImportRows);
            existingGermplasmByGID = initializeExistingGermplasmByGID(program, experimentImportRows);

        } else {

            // can't proceed if the import has a mix of ObsUnitId for some but not all rows
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MISSING_OBS_UNIT_ID_ERROR);
        }
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
            boolean commit) throws ApiException, ValidatorException, MissingRequiredInfoException, UnprocessableEntityException {
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

        List<Trait> referencedTraits = verifyTraits(program.getId(), phenotypeCols, timestampCols);

        //Now know timestamps all valid phenotypes, can associate with phenotype column name for easy retrieval
        for (Column<?> tsColumn : timestampCols) {
            timeStampColByPheno.put(tsColumn.name().replaceFirst(TIMESTAMP_REGEX, StringUtils.EMPTY), tsColumn);
        }

        // add "New" pending data to the BrapiData objects
        initNewBrapiData(importRows, phenotypeCols, program, user, referencedTraits, commit);

        prepareDataForValidation(importRows, phenotypeCols, mappedBrAPIImport);

        validateFields(importRows, validationErrors, mappedBrAPIImport, referencedTraits, program, phenotypeCols, commit, user);

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
                .getMutationsByObjectId(trialByNameNoScope, BrAPITrial::getTrialDbId);

        Map<String, BrAPIObservation> mutatedObservationByDbId = ProcessorData
                .getMutationsByObjectId(observationByHash, BrAPIObservation::getObservationDbId);

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
                .getMutationsByObjectId(obsVarDatasetByName, BrAPIListSummary::getListDbId);

        List<BrAPIObservationUnit> newObservationUnits = ProcessorData.getNewObjects(this.observationUnitByNameNoScope);

        // filter out observations with no 'value' so they will not be saved
        List<BrAPIObservation> newObservations = ProcessorData.getNewObjects(this.observationByHash)
                                                              .stream()
                                                              .filter(obs -> !obs.getValue().isBlank())
                                                              .collect(Collectors.toList());

        AuthenticatedUser actingUser = new AuthenticatedUser(upload.getUpdatedByUser().getName(), new ArrayList<>(), upload.getUpdatedByUser().getId(), new ArrayList<>());

        try {
            List<BrAPIListSummary> createdDatasets = new ArrayList<>(brAPIListDAO.createBrAPILists(newDatasetRequests, program.getId(), upload));
            createdDatasets.forEach(summary -> obsVarDatasetByName.get(summary.getListName()).getBrAPIObject().setListDbId(summary.getListDbId()));

            List<BrAPITrial> createdTrials = new ArrayList<>(brapiTrialDAO.createBrAPITrials(newTrials, program.getId(), upload));
            // set the DbId to the for each newly created trial
            for (BrAPITrial createdTrial : createdTrials) {
                String createdTrialName = Utilities.removeProgramKey(createdTrial.getTrialName(), program.getKey());
                this.trialByNameNoScope.get(createdTrialName)
                        .getBrAPIObject()
                        .setTrialDbId(createdTrial.getTrialDbId());
            }

            List<ProgramLocation> createdLocations = new ArrayList<>(locationService.create(actingUser, program.getId(), newLocations));
            // set the DbId to the for each newly created location
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

        mutatedObservationByDbId.forEach((id, observation) ->  {
            try {
                if (observation == null) {
                    throw new Exception("Null observation");
                }
                BrAPIObservation updatedObs = brAPIObservationDAO.updateBrAPIObservation(id, observation, program.getId());

                if (updatedObs == null) {
                    throw new Exception("Null updated observation");
                }

                if (!Objects.equals(observation.getValue(), updatedObs.getValue())
                        || !Objects.equals(observation.getObservationTimeStamp(), updatedObs.getObservationTimeStamp())) {
                    String message;
                    if(!Objects.equals(observation.getValue(), updatedObs.getValue())) {
                        message = String.format("Updated observation, %s, from BrAPI service does not match requested update %s.", updatedObs.getValue(), observation.getValue());
                    } else {
                        message = String.format("Updated observation timestamp, %s, from BrAPI service does not match requested update timestamp %s.", updatedObs.getObservationTimeStamp(), observation.getObservationTimeStamp());
                    }
                    throw new Exception(message);
                }
            } catch (ApiException e) {
                log.error("Error updating observation: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException("Error saving experiment import", e);
            } catch (Exception e) {
                log.error("Error updating observation: ", e);
                throw new InternalServerException(e.getMessage(), e);
            }
        });
        log.debug("experiment import complete");
    }

    private void prepareDataForValidation(List<BrAPIImport> importRows, List<Column<?>> phenotypeCols, Map<Integer, PendingImport> mappedBrAPIImport) {
        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);
            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(rowNum, new PendingImport());
            List<PendingImportObject<BrAPIObservation>> observations = mappedImportRow.getObservations();
            String observationHash;
            if (hasAllReferenceUnitIds) {
                String refOUId = importRow.getObsUnitID();
                mappedImportRow.setTrial(pendingTrialByOUId.get(refOUId));
                mappedImportRow.setLocation(pendingLocationByOUId.get(refOUId));
                mappedImportRow.setStudy(pendingStudyByOUId.get(refOUId));
                mappedImportRow.setObservationUnit(pendingObsUnitByOUId.get(refOUId));
                mappedImportRow.setGermplasm(pendingGermplasmByOUId.get(refOUId));

                // loop over phenotype column observation data for current row
                for (Column<?> column : phenotypeCols) {
                    observationHash = getObservationHash(
                            pendingStudyByOUId.get(refOUId).getBrAPIObject().getStudyName() +
                            pendingObsUnitByOUId.get(refOUId).getBrAPIObject().getObservationUnitName(),
                            getVariableNameFromColumn(column),
                            pendingStudyByOUId.get(refOUId).getBrAPIObject().getStudyName()
                    );

                    // if value was blank won't be entry in map for this observation
                    observations.add(observationByHash.get(observationHash));
                }

            } else {
                mappedImportRow.setTrial(trialByNameNoScope.get(importRow.getExpTitle()));
                mappedImportRow.setLocation(locationByName.get(importRow.getEnvLocation()));
                mappedImportRow.setStudy(studyByNameNoScope.get(importRow.getEnv()));
                mappedImportRow.setObservationUnit(observationUnitByNameNoScope.get(createObservationUnitKey(importRow)));
                mappedImportRow.setGermplasm(getGidPIO(importRow));

                // loop over phenotype column observation data for current row
                for (Column<?> column : phenotypeCols) {

                    // if value was blank won't be entry in map for this observation
                    observations.add(observationByHash.get(getImportObservationHash(importRow, getVariableNameFromColumn(column))));
                }
            }

            mappedBrAPIImport.put(rowNum, mappedImportRow);
        }
    }

    private List<Trait> verifyTraits(UUID programId, List<Column<?>> phenotypeCols, List<Column<?>> timestampCols) {
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
            Collection<String> upperCaseVarNames = varNames.stream().map(String::toUpperCase).collect(Collectors.toList());
            List<Trait> traits = ontologyService.getTraitsByProgramId(programId, true);
            // filter out just traits specified in file
            return traits.stream()
                         .filter(e -> upperCaseVarNames.contains(e.getObservationVariableName().toUpperCase()))
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

    private void initNewBrapiData(
            List<BrAPIImport> importRows,
            List<Column<?>> phenotypeCols,
            Program program,
            User user,
            List<Trait> referencedTraits,
            boolean commit
    ) throws UnprocessableEntityException, ApiException, MissingRequiredInfoException {

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
        existingObsByObsHash = fetchExistingObservations(referencedTraits, program);

        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);

            PendingImportObject<BrAPITrial> trialPIO = null;
            try {
                trialPIO = fetchOrCreateTrialPIO(program, user, commit, importRow, expNextVal);
            } catch (UnprocessableEntityException e) {
                throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
            }

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

                // get the study year either referenced from the observation unit or listed explicitly on the import row
                String studyYear = hasAllReferenceUnitIds ? studyPIO.getBrAPIObject().getSeasons().get(0) : importRow.getEnvYear();
                String seasonDbId = yearToSeasonDbId(studyYear, program.getId());
                fetchOrCreateObservationPIO(
                        program,
                        user,
                        importRow,
                        column,    //column.name() gets phenotype name
                        rowNum,
                        dateTimeValue,
                        commit,
                        seasonDbId,
                        obsUnitPIO,
                        studyPIO,
                        referencedTraits
                );
            }
        }
    }

    private String createObservationUnitKey(ExperimentObservation importRow) {
        return createObservationUnitKey(importRow.getEnv(), importRow.getExpUnitId());
    }

    private String createObservationUnitKey(String studyName, String obsUnitName) {
        return studyName + obsUnitName;
    }

    /**
     * This method is responsible for generating a hash based on the import observation unit information.
     * It takes the observation unit name, variable name, and study name as input parameters.
     * The observation unit key is created using the study name and observation unit name.
     * The hash is generated based on the observation unit key, variable name, and study name.
     *
     * @param obsUnitName The name of the observation unit being imported.
     * @param variableName The name of the variable associated with the observation unit.
     * @param studyName The name of the study associated with the observation unit.
     * @return A string representing the hash of the import observation unit information.
     */
    private String getImportObservationHash(String obsUnitName, String variableName, String studyName) {
        return getObservationHash(createObservationUnitKey(studyName, obsUnitName), variableName, studyName);
    }

    private String getImportObservationHash(ExperimentObservation importRow, String variableName) {
        return getObservationHash(createObservationUnitKey(importRow), variableName, importRow.getEnv());
    }

    private String getObservationHash(String observationUnitName, String variableName, String studyName) {
        String concat = DigestUtils.sha256Hex(observationUnitName) +
                DigestUtils.sha256Hex(variableName) +
                DigestUtils.sha256Hex(StringUtils.defaultString(studyName));
        return DigestUtils.sha256Hex(concat);
    }

    private void validateFields(List<BrAPIImport> importRows, ValidationErrors validationErrors, Map<Integer, PendingImport> mappedBrAPIImport, List<Trait> referencedTraits, Program program,
                                List<Column<?>> phenotypeCols, boolean commit, User user) {
        //fetching any existing observations for any OUs in the import
        CaseInsensitiveMap<String, Trait> colVarMap = new CaseInsensitiveMap<>();
        for ( Trait trait: referencedTraits) {
            colVarMap.put(trait.getObservationVariableName(),trait);
        }
        Set<String> uniqueStudyAndObsUnit = new HashSet<>();
        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);
            PendingImport mappedImportRow = mappedBrAPIImport.get(rowNum);
            if (hasAllReferenceUnitIds) {
                validateObservations(validationErrors, rowNum, importRow, phenotypeCols, colVarMap, commit, user);
            } else {
                if (StringUtils.isNotBlank(importRow.getGid())) { // if GID is blank, don't bother to check if it is valid.
                    validateGermplasm(importRow, validationErrors, rowNum, mappedImportRow.getGermplasm());
                }
                validateTestOrCheck(importRow, validationErrors, rowNum);
                validateConditionallyRequired(validationErrors, rowNum, importRow, program, commit);
                validateObservationUnits(validationErrors, uniqueStudyAndObsUnit, rowNum, importRow);
                validateObservations(validationErrors, rowNum, importRow, phenotypeCols, colVarMap, commit, user);
            }
        }
    }

    private void validateObservationUnits(
            ValidationErrors validationErrors,
            Set<String> uniqueStudyAndObsUnit,
            int rowNum,
            ExperimentObservation importRow
    ) {
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

        studyNameByDbId.keySet().forEach(studyDbId -> {
            try {
                brAPIObservationUnitDAO.getObservationUnitsForStudyDbId(studyDbId, program).forEach(ou -> {
                    if(StringUtils.isNotBlank(ou.getObservationUnitDbId())) {
                        ouDbIds.add(ou.getObservationUnitDbId());
                    }
                    ouNameByDbId.put(ou.getObservationUnitDbId(), Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getObservationUnitName(), program.getKey()));
                });
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
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

    private void validateObservations(ValidationErrors validationErrors,
                                      int rowNum,
                                      ExperimentObservation importRow,
                                      List<Column<?>> phenotypeCols,
                                      CaseInsensitiveMap<String, Trait> colVarMap,
                                      boolean commit,
                                      User user) {
        phenotypeCols.forEach(phenoCol -> {
            String importHash;
            String importObsValue = phenoCol.getString(rowNum);

            if (hasAllReferenceUnitIds) {
                importHash = getImportObservationHash(
                        pendingObsUnitByOUId.get(importRow.getObsUnitID()).getBrAPIObject().getObservationUnitName(),
                        getVariableNameFromColumn(phenoCol),
                        pendingStudyByOUId.get(importRow.getObsUnitID()).getBrAPIObject().getStudyName()
                );

            } else {
                importHash = getImportObservationHash(importRow, phenoCol.name());
            }

            // error if import observation data already exists and user has not selected to overwrite
            if(commit && "false".equals(importRow.getOverwrite() == null ? "false" : importRow.getOverwrite()) &&
                    this.existingObsByObsHash.containsKey(importHash) &&
                    StringUtils.isNotBlank(phenoCol.getString(rowNum)) &&
                    !this.existingObsByObsHash.get(importHash).getValue().equals(phenoCol.getString(rowNum))) {
                addRowError(
                        phenoCol.name(),
                        String.format("Value already exists for ObsUnitId: %s, Phenotype: %s", importRow.getObsUnitID(), phenoCol.name()),
                        validationErrors, rowNum
                );

            // preview case where observation has already been committed and the import row ObsVar data differs from what
            // had been saved prior to import
            } else if (existingObsByObsHash.containsKey(importHash) && !isObservationMatched(importHash, importObsValue, phenoCol, rowNum)) {

                // add a change log entry when updating the value of an observation
                if (commit) {
                    BrAPIObservation pendingObservation = observationByHash.get(importHash).getBrAPIObject();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:hh-mm-ssZ");
                    String timestamp = formatter.format(OffsetDateTime.now());
                    String reason = importRow.getOverwriteReason() != null ? importRow.getOverwriteReason() : "";
                    String prior = "";
                    if (isValueMatched(importHash, importObsValue)) {
                        prior.concat(existingObsByObsHash.get(importHash).getValue());
                    }
                    if (timeStampColByPheno.containsKey(phenoCol.name()) && isTimestampMatched(importHash, timeStampColByPheno.get(phenoCol.name()).getString(rowNum))) {
                        prior = prior.isEmpty() ? prior : prior.concat(" ");
                        prior.concat(existingObsByObsHash.get(importHash).getObservationTimeStamp().toString());
                    }
                    ChangeLogEntry change = new ChangeLogEntry(prior,
                            reason,
                            user.getId(),
                            timestamp
                    );

                    // create the changelog field in additional info if it does not already exist
                    if (pendingObservation.getAdditionalInfo().isJsonNull()) {
                        pendingObservation.setAdditionalInfo(new JsonObject());
                        pendingObservation.getAdditionalInfo().add(BrAPIAdditionalInfoFields.CHANGELOG, new JsonArray());
                    }

                    if (pendingObservation.getAdditionalInfo() != null && !pendingObservation.getAdditionalInfo().has(BrAPIAdditionalInfoFields.CHANGELOG)) {
                        pendingObservation.getAdditionalInfo().add(BrAPIAdditionalInfoFields.CHANGELOG, new JsonArray());
                    }

                    // add a new entry to the changelog
                    pendingObservation.getAdditionalInfo().get(BrAPIAdditionalInfoFields.CHANGELOG).getAsJsonArray().add(gson.toJsonTree(change).getAsJsonObject());
                }

                // preview case where observation has already been committed and import ObsVar data is the
                // same as has been committed prior to import
            } else if(isObservationMatched(importHash, importObsValue, phenoCol, rowNum)) {
                BrAPIObservation existingObs = this.existingObsByObsHash.get(importHash);
                existingObs.setObservationVariableName(phenoCol.name());
                observationByHash.get(importHash).setState(ImportObjectState.EXISTING);
                observationByHash.get(importHash).setBrAPIObject(existingObs);

                // preview case where observation has already been committed and import ObsVar data is empty prior to import
            } else if(!existingObsByObsHash.containsKey(importHash) && (StringUtils.isBlank(phenoCol.getString(rowNum)))) {
                observationByHash.get(importHash).setState(ImportObjectState.EXISTING);
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
                String studyYear = StringUtils.defaultString( this.studyByNameNoScope.get(importRow.getEnv()).getBrAPIObject().getSeasons().get(0) );
                String rowYear = importRow.getEnvYear();
                if(commit) {
                    rowYear = this.yearToSeasonDbId(importRow.getEnvYear(), program.getId());
                }
                if(StringUtils.isNotBlank(studyYear) && !studyYear.equals(rowYear)) {
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
            //Check if existing environment. If so, ObsUnitId must be assigned
            validateRequiredCell(
                    importRow.getObsUnitID(),
                    Columns.OBS_UNIT_ID,
                    MISSING_OBS_UNIT_ID_ERROR,
                    validationErrors,
                    rowNum
            );
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

        int numExistingObservations = Math.toIntExact(
                this.observationByHash.values()
                        .stream()
                        .filter(preview -> preview != null && preview.getState() == ImportObjectState.EXISTING &&
                                !StringUtils.isBlank(preview.getBrAPIObject()
                                        .getValue()))
                        .count()
        );

        int numMutatedObservations = Math.toIntExact(
                this.observationByHash.values()
                        .stream()
                        .filter(preview -> preview != null && preview.getState() == ImportObjectState.MUTATED &&
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
        ImportPreviewStatistics existingObservationStats = ImportPreviewStatistics.builder()
                .newObjectCount(numExistingObservations)
                .build();
        ImportPreviewStatistics mutatedObservationStats = ImportPreviewStatistics.builder()
                .newObjectCount(numMutatedObservations)
                .build();

        return Map.of(
                "Environments", environmentStats,
                "Observation_Units", obdUnitStats,
                "GIDs", gidStats,
                "Observations", observationStats,
                "Existing_Observations", existingObservationStats,
                "Mutated_Observations", mutatedObservationStats
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

    private PendingImportObject<BrAPIGermplasm> getGidPIO(ExperimentObservation importRow) {
        if (this.existingGermplasmByGID.containsKey(importRow.getGid())) {
            return existingGermplasmByGID.get(importRow.getGid());
        }

        return null;
    }

    private PendingImportObject<BrAPIObservationUnit> fetchOrCreateObsUnitPIO(Program program, boolean commit, String envSeqValue, ExperimentObservation importRow) throws ApiException, MissingRequiredInfoException, UnprocessableEntityException {
        PendingImportObject<BrAPIObservationUnit> pio;
        String key = createObservationUnitKey(importRow);
        if (hasAllReferenceUnitIds) {
            pio = pendingObsUnitByOUId.get(importRow.getObsUnitID());
        } else if (observationUnitByNameNoScope.containsKey(key)) {
            pio = observationUnitByNameNoScope.get(key);
        } else {
            String germplasmName = "";
            if (this.existingGermplasmByGID.get(importRow.getGid()) != null) {
                germplasmName = this.existingGermplasmByGID.get(importRow.getGid())
                                                           .getBrAPIObject()
                                                           .getGermplasmName();
            }
            PendingImportObject<BrAPITrial> trialPIO = trialByNameNoScope.get(importRow.getExpTitle());;
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
            BrAPIObservationUnit newObservationUnit = importRow.constructBrAPIObservationUnit(program, envSeqValue, commit, germplasmName, importRow.getGid(), BRAPI_REFERENCE_SOURCE, trialID, datasetId, studyID, id);

            // check for existing units if this is an existing study
            if (studyPIO.getBrAPIObject().getStudyDbId() != null) {
                List<BrAPIObservationUnit> existingOUs = brAPIObservationUnitDAO.getObservationUnitsForStudyDbId(studyPIO.getBrAPIObject().getStudyDbId(), program);
                List<BrAPIObservationUnit> matchingOU = existingOUs.stream().filter(ou -> importRow.getExpUnitId().equals(Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getObservationUnitName(), program.getKey()))).collect(Collectors.toList());
                if (matchingOU.isEmpty()) {
                    throw new MissingRequiredInfoException(MISSING_OBS_UNIT_ID_ERROR);
                } else {
                    pio = new PendingImportObject<>(ImportObjectState.EXISTING, (BrAPIObservationUnit) Utilities.formatBrapiObjForDisplay(matchingOU.get(0), BrAPIObservationUnit.class, program));
                }
            } else {
                pio = new PendingImportObject<>(ImportObjectState.NEW, newObservationUnit, id);
            }
            this.observationUnitByNameNoScope.put(key, pio);
        }
        return pio;
    }

    boolean isTimestampMatched(String observationHash, String timeStamp) {
        OffsetDateTime priorStamp = existingObsByObsHash.get(observationHash).getObservationTimeStamp();
        if (priorStamp == null) {
            return timeStamp == null;
        }
        return priorStamp.isEqual(OffsetDateTime.parse(timeStamp));
    }

    boolean isValueMatched(String observationHash, String value) {
        if (!existingObsByObsHash.containsKey(observationHash) || existingObsByObsHash.get(observationHash).getValue() == null) {
            return value == null;
        }
        return existingObsByObsHash.get(observationHash).getValue().equals(value);
    }

    boolean isObservationMatched(String observationHash, String value, Column phenoCol, Integer rowNum) {
        if (timeStampColByPheno.isEmpty() || !timeStampColByPheno.containsKey(phenoCol.name())) {
            return isValueMatched(observationHash, value);
        } else {
            String importObsTimestamp = timeStampColByPheno.get(phenoCol.name()).getString(rowNum);
            return isTimestampMatched(observationHash, importObsTimestamp) && isValueMatched(observationHash, value);
        }
    }

    private void fetchOrCreateObservationPIO(Program program,
                                             User user,
                                             ExperimentObservation importRow,
                                             Column column,
                                             Integer rowNum,
                                             String timeStampValue,
                                             boolean commit,
                                             String seasonDbId,
                                             PendingImportObject<BrAPIObservationUnit> obsUnitPIO,
                                             PendingImportObject<BrAPIStudy> studyPIO,
                                             List<Trait> referencedTraits) throws ApiException, UnprocessableEntityException {
        PendingImportObject<BrAPIObservation> pio;
        BrAPIObservation newObservation;
        String variableName = column.name();
        String value = column.getString(rowNum);
        String key;
        if (hasAllReferenceUnitIds) {
            String unitName = obsUnitPIO.getBrAPIObject().getObservationUnitName();
            String studyName = studyPIO.getBrAPIObject().getStudyName();
            key = getObservationHash(studyName + unitName, variableName, studyName);
        } else {
            key = getImportObservationHash(importRow, variableName);
        }

        if (existingObsByObsHash.containsKey(key)) {
            if (!isObservationMatched(key, value, column, rowNum)){

                // prior observation with updated value
                newObservation = gson.fromJson(gson.toJson(existingObsByObsHash.get(key)), BrAPIObservation.class);
                if (!isValueMatched(key, value)){
                    newObservation.setValue(value);
                } else if (!isTimestampMatched(key, timeStampValue)) {
                    DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
                    String formattedTimeStampValue = formatter.format(OffsetDateTime.parse(timeStampValue));
                    newObservation.setObservationTimeStamp(OffsetDateTime.parse(formattedTimeStampValue));
                }
                pio = new PendingImportObject<>(ImportObjectState.MUTATED, (BrAPIObservation) Utilities.formatBrapiObjForDisplay(newObservation, BrAPIObservation.class, program));
            } else {

                // prior observation
                pio = new PendingImportObject<>(ImportObjectState.EXISTING, (BrAPIObservation) Utilities.formatBrapiObjForDisplay(existingObsByObsHash.get(key), BrAPIObservation.class, program));
            }

            observationByHash.put(key, pio);
        } else if (!this.observationByHash.containsKey(key)){

            // new observation
            PendingImportObject<BrAPITrial> trialPIO = hasAllReferenceUnitIds ?
                    getSingleEntryValue(trialByNameNoScope, MULTIPLE_EXP_TITLES) : trialByNameNoScope.get(importRow.getExpTitle());

            UUID trialID = trialPIO.getId();
            UUID studyID = studyPIO.getId();
            UUID id = UUID.randomUUID();
            newObservation = importRow.constructBrAPIObservation(value, variableName, seasonDbId, obsUnitPIO.getBrAPIObject(), commit, program, user, BRAPI_REFERENCE_SOURCE, trialID, studyID, obsUnitPIO.getId(), id);
            //NOTE: Can't parse invalid timestamp value, so have to skip if invalid.
            // Validation error should be thrown for offending value, but that doesn't happen until later downstream
            if (timeStampValue != null && !timeStampValue.isBlank() && (validDateValue(timeStampValue) || validDateTimeValue(timeStampValue))) {
                newObservation.setObservationTimeStamp(OffsetDateTime.parse(timeStampValue));
            }

            newObservation.setStudyDbId(studyPIO.getId().toString()); //set as the BI ID to facilitate looking up studies when saving new observations

            pio = new PendingImportObject<>(ImportObjectState.NEW, newObservation);
            this.observationByHash.put(key, pio);
        }
    }
    private void addObsVarsToDatasetDetails(PendingImportObject<BrAPIListDetails> pio, List<Trait> referencedTraits, Program program) {
        BrAPIListDetails details = pio.getBrAPIObject();
        referencedTraits.forEach(trait -> {
            String id = Utilities.appendProgramKey(trait.getObservationVariableName(), program.getKey());

            // TODO - Don't append the key if connected to a brapi service operating with legacy data(no appended program key)

            if (!details.getData().contains(id) && ImportObjectState.EXISTING != pio.getState()) {
                details.getData().add(id);
            }
            if (!details.getData().contains(id) && ImportObjectState.EXISTING == pio.getState()) {
                details.getData().add(id);
                pio.setState(ImportObjectState.MUTATED);
            }
        });
    }
    private void fetchOrCreateDatasetPIO(ExperimentObservation importRow, Program program, List<Trait> referencedTraits) throws UnprocessableEntityException {
        PendingImportObject<BrAPIListDetails> pio;
        PendingImportObject<BrAPITrial> trialPIO = hasAllReferenceUnitIds ?
                getSingleEntryValue(trialByNameNoScope, MULTIPLE_EXP_TITLES) : trialByNameNoScope.get(importRow.getExpTitle());
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
    }

    private PendingImportObject<BrAPIStudy> fetchOrCreateStudyPIO(
            Program program,
            boolean commit,
            String expSequenceValue,
            ExperimentObservation importRow,
            Supplier<BigInteger> envNextVal
    ) throws UnprocessableEntityException {
        PendingImportObject<BrAPIStudy> pio;
        if (hasAllReferenceUnitIds) {
            String studyName = Utilities.removeProgramKeyAndUnknownAdditionalData(
                    pendingObsUnitByOUId.get(importRow.getObsUnitID()).getBrAPIObject().getStudyName(),
                    program.getKey()
            );
            pio = studyByNameNoScope.get(studyName);
            if (!commit){
                addYearToStudyAdditionalInfo(program, pio.getBrAPIObject());
            }
        } else if (studyByNameNoScope.containsKey(importRow.getEnv())) {
            pio = studyByNameNoScope.get(importRow.getEnv());
            if (!commit){
                addYearToStudyAdditionalInfo(program, pio.getBrAPIObject());
            }
        } else {
            PendingImportObject<BrAPITrial> trialPIO = hasAllReferenceUnitIds ?
                    getSingleEntryValue(trialByNameNoScope, MULTIPLE_EXP_TITLES) : trialByNameNoScope.get(importRow.getExpTitle());
            UUID trialID = trialPIO.getId();
            UUID id = UUID.randomUUID();
            BrAPIStudy newStudy = importRow.constructBrAPIStudy(program, commit, BRAPI_REFERENCE_SOURCE, expSequenceValue, trialID, id, envNextVal);
            newStudy.setLocationDbId(this.locationByName.get(importRow.getEnvLocation()).getId().toString()); //set as the BI ID to facilitate looking up locations when saving new studies

            // It is assumed that the study has only one season, And that the Years and not
            // the dbId's are stored in getSeason() list.
            String year = newStudy.getSeasons().get(0); // It is assumed that the study has only one season
            if (commit) {
                if(StringUtils.isNotBlank(year)) {
                    String seasonID = this.yearToSeasonDbId(year, program.getId());
                    newStudy.setSeasons(Collections.singletonList(seasonID));
                }
            } else {
                addYearToStudyAdditionalInfo(program, newStudy, year);
            }

            pio = new PendingImportObject<>(ImportObjectState.NEW, newStudy, id);
            this.studyByNameNoScope.put(importRow.getEnv(), pio);
        }
        return pio;
    }


    /*
     * this finds the YEAR from the season list on the BrAPIStudy and then
     * will add the year to the additionalInfo-field of the BrAPIStudy
     * */
    private void addYearToStudyAdditionalInfo(Program program, BrAPIStudy study) {
        JsonObject additionalInfo = study.getAdditionalInfo();

        //if it is already there, don't add it.
        if(additionalInfo==null || additionalInfo.get(BrAPIAdditionalInfoFields.ENV_YEAR)==null) {
            String year = study.getSeasons().get(0);
            addYearToStudyAdditionalInfo(program, study, year);
        }
    }


    /*
    * this will add the given year to the additionalInfo field of the BrAPIStudy (if it does not already exist)
    * */
    private void addYearToStudyAdditionalInfo(Program program, BrAPIStudy study, String year) {
        JsonObject additionalInfo = study.getAdditionalInfo();
        if (additionalInfo==null){
            additionalInfo = new JsonObject();
            study.setAdditionalInfo(additionalInfo);
        }
        if( additionalInfo.get(BrAPIAdditionalInfoFields.ENV_YEAR)==null) {
            additionalInfo.addProperty(BrAPIAdditionalInfoFields.ENV_YEAR, year);
        }
    }

    private void fetchOrCreateLocationPIO(ExperimentObservation importRow) {
        PendingImportObject<ProgramLocation> pio;
        String envLocationName = hasAllReferenceUnitIds ?
                pendingObsUnitByOUId.get(importRow.getObsUnitID()).getBrAPIObject().getLocationName() : importRow.getEnvLocation();
        if (!locationByName.containsKey((importRow.getEnvLocation()))) {
            ProgramLocation newLocation = new ProgramLocation();
            newLocation.setName(envLocationName);
            pio = new PendingImportObject<>(ImportObjectState.NEW, newLocation, UUID.randomUUID());
            this.locationByName.put(envLocationName, pio);
        }
    }

    private PendingImportObject<BrAPITrial> fetchOrCreateTrialPIO(
            Program program,
            User user,
            boolean commit,
            ExperimentObservation importRow,
            Supplier<BigInteger> expNextVal
    ) throws UnprocessableEntityException {
        PendingImportObject<BrAPITrial> trialPio;

        // use the prior trial if observation unit IDs are supplied
        if (hasAllReferenceUnitIds) {
            trialPio = getSingleEntryValue(trialByNameNoScope, MULTIPLE_EXP_TITLES);

        // otherwise create a new trial, but there can be only one allowed
        } else {
            if (trialByNameNoScope.containsKey(importRow.getExpTitle())) {
                PendingImportObject<BrAPIStudy> envPio;
                trialPio = trialByNameNoScope.get(importRow.getExpTitle());
                envPio = this.studyByNameNoScope.get(importRow.getEnv());
                if  (trialPio!=null &&  ImportObjectState.EXISTING==trialPio.getState() && (StringUtils.isBlank( importRow.getObsUnitID() )) && (envPio!=null && ImportObjectState.EXISTING==envPio.getState() ) ){
                    throw new UnprocessableEntityException(PREEXISTING_EXPERIMENT_TITLE);
                }
            } else if (!trialByNameNoScope.isEmpty()) {
                throw new UnprocessableEntityException(MULTIPLE_EXP_TITLES);
            } else {
                UUID id = UUID.randomUUID();
                String expSeqValue = null;
                if (commit) {
                    expSeqValue = expNextVal.get().toString();
                }
                BrAPITrial newTrial = importRow.constructBrAPITrial(program, user, commit, BRAPI_REFERENCE_SOURCE, id, expSeqValue);
                trialPio = new PendingImportObject<>(ImportObjectState.NEW, newTrial, id);
                this.trialByNameNoScope.put(importRow.getExpTitle(), trialPio);
            }
        }
        return trialPio;
    }

    private void updateObservationDependencyValues(Program program) {
        String programKey = program.getKey();

        // update the observations study DbIds, Observation Unit DbIds and Germplasm DbIds
        this.observationUnitByNameNoScope.values().stream()
                                         .map(PendingImportObject::getBrAPIObject)
                                         .forEach(obsUnit -> updateObservationDbIds(obsUnit, programKey));

        // Update ObservationVariable DbIds
        List<Trait> traits = getTraitList(program);
        CaseInsensitiveMap<String, Trait> traitMap = new CaseInsensitiveMap<>();
        for ( Trait trait: traits) {
            traitMap.put(trait.getObservationVariableName(),trait);
        }
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
                                         .filter(obsUnit -> germplasm.getAccessionNumber() != null &&
                                                 germplasm.getAccessionNumber().equals(obsUnit
                                                         .getBrAPIObject()
                                                         .getAdditionalInfo().getAsJsonObject()
                                                         .get(BrAPIAdditionalInfoFields.GID).getAsString()))
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

                    ExperimentObservation row = rowByObsUnitId.get(idRef.getReferenceId());
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

    /**
     * Maps pending locations by Observation Unit (OU) Id based on given parameters.
     *
     * This method takes in a unitId, BrAPIObservationUnit unit, Maps of studyByOUId, locationByName,
     * and locationByOUId. It then associates the location of the observation unit with the respective OU Id.
     * If the locationName is not null for the unit, it is directly added to locationByOUId.
     * If the locationName is null, it checks the studyByOUId map for a location related to the unit.
     * If a location related to the unit is found, it maps that location with the respective OU Id.
     * If no location is found, it throws an IllegalStateException.
     *
     * @param unitId the Observation Unit Id
     * @param unit the BrAPIObservationUnit object
     * @param studyByOUId a Map of Study by Observation Unit Id
     * @param locationByName a Map of Location by Name
     * @param locationByOUId a Map of Location by Observation Unit Id
     * @return the updated locationByOUId map after mapping the pending locations
     * @throws IllegalStateException if the Observation Unit is missing a location
     */
    private Map<String, PendingImportObject<ProgramLocation>> mapPendingLocationByOUId(
            String unitId,
            BrAPIObservationUnit unit,
            Map<String, PendingImportObject<BrAPIStudy>> studyByOUId,
            Map<String, PendingImportObject<ProgramLocation>> locationByName,
            Map<String, PendingImportObject<ProgramLocation>> locationByOUId
    ) {
        if (unit.getLocationName() != null) {
            locationByOUId.put(unitId, locationByName.get(unit.getLocationName()));
        } else if (studyByOUId.get(unitId) != null && studyByOUId.get(unitId).getBrAPIObject().getLocationName() != null) {
            locationByOUId.put(
                    unitId,
                    locationByName.get(studyByOUId.get(unitId).getBrAPIObject().getLocationName())
            );
        } else {
            throw new IllegalStateException("Observation unit missing location: " + unitId);
        }

        return locationByOUId;
    }

    private Map<String, PendingImportObject<BrAPIStudy>> mapPendingStudyByOUId(
            String unitId,
            BrAPIObservationUnit unit,
            Map<String, PendingImportObject<BrAPIStudy>> studyByName,
            Map<String, PendingImportObject<BrAPIStudy>> studyByOUId,
            Program program
    ) {
        if (unit.getStudyName() != null) {
            String studyName = Utilities.removeProgramKeyAndUnknownAdditionalData(unit.getStudyName(), program.getKey());
            studyByOUId.put(unitId, studyByName.get(studyName));
        } else {
            throw new IllegalStateException("Observation unit missing study name: " + unitId);
        }

        return studyByOUId;
    }
    private Map<String, PendingImportObject<BrAPITrial>> mapPendingTrialByOUId(
            String unitId,
            BrAPIObservationUnit unit,
            Map<String, PendingImportObject<BrAPITrial>> trialByName,
            Map<String, PendingImportObject<BrAPIStudy>> studyByName,
            Map<String, PendingImportObject<BrAPITrial>> trialByOUId,
            Program program
    ) {
        String trialName;
        if (unit.getTrialName() != null) {
            trialName = Utilities.removeProgramKeyAndUnknownAdditionalData(unit.getTrialName(), program.getKey());
        } else if (unit.getStudyName() != null) {
            String studyName = Utilities.removeProgramKeyAndUnknownAdditionalData(unit.getStudyName(), program.getKey());
            trialName = Utilities.removeProgramKeyAndUnknownAdditionalData(
                    studyByName.get(studyName).getBrAPIObject().getTrialName(),
                    program.getKey()
            );
        } else {
            throw new IllegalStateException("Observation unit missing trial name and study name: " + unitId);
        }
        trialByOUId.put(unitId, trialByName.get(trialName));

        return trialByOUId;
    }
    private Map<String, PendingImportObject<BrAPIObservationUnit>> mapPendingObservationUnitByName(
            Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitById,
            Program program
    ) {
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitByName = new HashMap<>();
        for (Map.Entry<String, PendingImportObject<BrAPIObservationUnit>> entry : pendingUnitById.entrySet()) {
            String studyName = Utilities.removeProgramKeyAndUnknownAdditionalData(
                    entry.getValue().getBrAPIObject().getStudyName(),
                    program.getKey()
            );
            String observationUnitName = Utilities.removeProgramKeyAndUnknownAdditionalData(
                    entry.getValue().getBrAPIObject().getObservationUnitName(),
                    program.getKey()
            );
            pendingUnitByName.put(createObservationUnitKey(studyName, observationUnitName), entry.getValue());
        }
        return pendingUnitByName;
    }

    /**
     * Retrieves reference Observation Units based on a set of reference Observation Unit IDs and a Program.
     * Constructs DeltaBreed observation unit source for external references and sets up pending Observation Units.
     *
     * @param referenceOUIds A set of reference Observation Unit IDs to retrieve
     * @param program The Program associated with the Observation Units
     * @return A Map containing pending Observation Units by their ID
     * @throws ApiException if an error occurs during the process
     */
    private Map<String, PendingImportObject<BrAPIObservationUnit>> fetchReferenceObservationUnits(
            Set<String> referenceOUIds,
            Program program
    ) throws ApiException {
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitById = new HashMap<>();
        try {
            // Retrieve reference Observation Units based on IDs
            List<BrAPIObservationUnit> referenceObsUnits = brAPIObservationUnitDAO.getObservationUnitsById(
                new ArrayList<String>(referenceOUIds),
                program
            );    

            // Construct the DeltaBreed observation unit source for external references
            String deltaBreedOUSource = String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName());
            
            if (referenceObsUnits.size() == referenceOUIds.size()) {
                // Iterate through reference Observation Units
                referenceObsUnits.forEach(unit -> {
                    // Get external reference for the Observation Unit
                    BrAPIExternalReference unitXref = Utilities.getExternalReference(unit.getExternalReferences(), deltaBreedOUSource)
                        .orElseThrow(() -> new IllegalStateException("External reference does not exist for Deltabreed ObservationUnit ID"));

                    // Set pending Observation Unit by its ID
                    pendingUnitById.put(
                        unitXref.getReferenceId(),
                        new PendingImportObject<BrAPIObservationUnit>(
                            ImportObjectState.EXISTING, unit, UUID.fromString(unitXref.getReferenceId()))
                    );
                });
            } else {
                // Handle missing Observation Unit IDs
                List<String> missingIds = new ArrayList<>(referenceOUIds);
                Set<String> fetchedIds = referenceObsUnits.stream().map(unit ->
                        Utilities.getExternalReference(unit.getExternalReferences(), deltaBreedOUSource)
                        .orElseThrow(() -> new InternalServerException("External reference does not exist for Deltabreed ObservationUnit ID"))
                        .getReferenceId())
                        .collect(Collectors.toSet());
                missingIds.removeAll(fetchedIds);
                throw new IllegalStateException("Observation Units not found for ObsUnitId(s): " + String.join(COMMA_DELIMITER, missingIds));
            }

            return pendingUnitById;
        } catch (ApiException e) {
            log.error("Error fetching observation units: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new ApiException(e);
        }
    }

    private Map<String, PendingImportObject<BrAPITrial>> initializeTrialByNameNoScope(Program program, List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPITrial>> trialByName = new HashMap<>();

        initializeTrialsForExistingObservationUnits(program, trialByName);

        List<String> uniqueTrialNames = experimentImportRows.stream()
                                                            .filter(row -> StringUtils.isBlank(row.getObsUnitID()))
                                                            .map(ExperimentObservation::getExpTitle)
                                                            .distinct()
                                                            .collect(Collectors.toList());
        try {
            brapiTrialDAO.getTrialsByName(uniqueTrialNames, program).forEach(existingTrial ->
                    processAndCacheTrial(existingTrial, program, trialByName)
            );
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
        } catch (Exception e) {
            log.error("Error processing studies", e);
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
            for (BrAPIStudy existingStudy : existingStudies) {
                processAndCacheStudy(existingStudy, program, BrAPIStudy::getStudyName, studyByName);
            }
        } catch (ApiException e) {
            log.error("Error fetching studies: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        } catch (Exception e) {
            log.error("Error processing studies: ", e);
            throw new InternalServerException(e.toString(), e);
        }

        return studyByName;
    }

    private void initializeStudiesForExistingObservationUnits(
            Program program,
            Map<String, PendingImportObject<BrAPIStudy>> studyByName
    ) throws Exception {
        Set<String> studyDbIds = observationUnitByNameNoScope.values()
                .stream()
                .map(pio -> pio.getBrAPIObject()
                        .getStudyDbId())
                .collect(Collectors.toSet());

        List<BrAPIStudy> studies = fetchStudiesByDbId(studyDbIds, program);
        for (BrAPIStudy study : studies) {
            processAndCacheStudy(study, program, BrAPIStudy::getStudyName, studyByName);
        }
    }

    /**
     * Fetches a list of BrAPI studies by their study database IDs for a given program.
     *
     * This method queries the BrAPIStudyDAO to retrieve studies based on the provided study database IDs and the program.
     * It ensures that all requested study database IDs are found in the result set, throwing an IllegalStateException if any are missing.
     *
     * @param studyDbIds a Set of Strings representing the study database IDs to fetch
     * @param program the Program object representing the program context in which to fetch studies
     * @return a List of BrAPIStudy objects matching the provided study database IDs
     * @throws ApiException if there is an issue fetching the studies
     * @throws IllegalStateException if any requested study database IDs are not found in the result set
     */
    private List<BrAPIStudy> fetchStudiesByDbId(Set<String> studyDbIds, Program program) throws ApiException {
        List<BrAPIStudy> studies = brAPIStudyDAO.getStudiesByStudyDbId(studyDbIds, program);
        if (studies.size() != studyDbIds.size()) {
            List<String> missingIds = new ArrayList<>(studyDbIds);
            missingIds.removeAll(studies.stream().map(BrAPIStudy::getStudyDbId).collect(Collectors.toList()));
            throw new IllegalStateException(
                    "Study not found for studyDbId(s): " + String.join(COMMA_DELIMITER, missingIds));
        }
        return studies;
    }

    /**
     * Initializes a map of ProgramLocation objects by their names using the given Program and a map of BrAPIStudy objects by their names.
     *
     * This method takes a Program object and a map of BrAPIStudy objects by their names, retrieves the location database IDs from the studies,
     * and fetches existing ProgramLocation objects based on the database IDs. It then creates a map of ProgramLocation objects by their names
     * with PendingImportObject wrappers that indicate the state of the object as existing.
     *
     * @param program the Program object to associate with the locations
     * @param studyByName a map of BrAPIStudy objects by their names
     * @return a map of ProgramLocation objects by their names with PendingImportObject wrappers
     * @throws InternalServerException if an error occurs during the location retrieval process
     */
    Map<String, PendingImportObject<ProgramLocation>> initializeLocationByName(
            Program program,
            Map<String, PendingImportObject<BrAPIStudy>> studyByName) {
        Map<String, PendingImportObject<ProgramLocation>> locationByName = new HashMap<>();

        List<ProgramLocation> existingLocations = new ArrayList<>();
        if(studyByName.size() > 0) {
            Set<String> locationDbIds = studyByName.values()
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
        existingLocations.forEach(existingLocation -> locationByName.put(
                existingLocation.getName(),
                new PendingImportObject<>(ImportObjectState.EXISTING, existingLocation, existingLocation.getId())
            )
        );
        return locationByName;
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

    /**
     * Maps a given germplasm to an observation unit ID in a given map of germplasm by observation unit ID.
     *
     * This method retrieves the Global Identifier (GID) of the provided observation unit and uses it to lookup
     * the corresponding PendingImportObject<BrAPIGermplasm> in the map of germplasm by name. The found germplasm
     * object is then mapped to the observation unit ID in the provided map of germplasm by observation unit ID.
     * The updated map is returned after the mapping operation has been performed.
     *
     * @param unitId The observation unit ID to which the germplasm should be mapped.
     * @param unit The BrAPIObservationUnit object representing the observation unit.
     * @param germplasmByName The map of germplasm objects by name used to lookup the desired germplasm.
     * @param germplasmByOUId The map of germplasm objects by observation unit ID to update with the mapping result.
     * @return The updated map of germplasm objects by observation unit ID after mapping the germplasm to the provided observation unit ID.
     */
    private Map<String, PendingImportObject<BrAPIGermplasm>> mapGermplasmByOUId(
            String unitId,
            BrAPIObservationUnit unit,
            Map<String, PendingImportObject<BrAPIGermplasm>> germplasmByName,
            Map<String, PendingImportObject<BrAPIGermplasm>> germplasmByOUId) {
        String gid = unit.getAdditionalInfo().getAsJsonObject().get(BrAPIAdditionalInfoFields.GID).getAsString();
        germplasmByOUId.put(unitId, germplasmByName.get(gid));

        return germplasmByOUId;
    }

    /**
     * Maps the pending observation dataset by OU Id based on the given inputs.
     * This function checks if the trialByOUId map is not empty, the obsVarDatasetByName map is not empty,
     * and if the first entry in the trialByOUId map contains observation dataset id in its additional info.
     * If the conditions are met, it adds the pending import object from the obsVarDatasetByName map to the
     * obsVarDatasetByOUId map using the unitId as the key.
     *
     * @param unitId the unit ID based on which the mapping is done
     * @param trialByOUId a map containing pending import objects with BrAPITrial as the value, mapped by OU Id
     * @param obsVarDatasetByName a map containing pending import objects with BrAPIListDetails as the value, mapped by dataset name
     * @param obsVarDatasetByOUId a map containing pending import objects with BrAPIListDetails as the value, mapped by OU Id
     * @return the updated obsVarDatasetByOUId map after potential addition of a pending import object
     */
    private Map<String, PendingImportObject<BrAPIListDetails>> mapPendingObsDatasetByOUId(
            String unitId,
            Map<String, PendingImportObject<BrAPITrial>> trialByOUId,
            Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName,
            Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByOUId) {
        if (!trialByOUId.isEmpty() && !obsVarDatasetByName.isEmpty() &&
                trialByOUId.values().iterator().next().getBrAPIObject().getAdditionalInfo().has(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID)) {
            obsVarDatasetByOUId.put(unitId, obsVarDatasetByName.values().iterator().next());
        }

        return obsVarDatasetByOUId;
    }

    /**
     * Initializes observation variable dataset for existing observation units. This function retrieves existing datasets related to observation variables for the specified trial and program, processes the dataset details, and caches the data accordingly.
     *
     * @param trialByName A map containing trial information indexed by trial name.
     * @param program The program to which the datasets are related.
     * @return A map of observation variable dataset objects indexed by dataset name.
     * @throws InternalServerException If the existing dataset summary is not retrieved from the BrAPI server, or an error occurs during API communication.
     */
    private Map<String, PendingImportObject<BrAPIListDetails>> initializeObsVarDatasetForExistingObservationUnits(
            Map<String, PendingImportObject<BrAPITrial>> trialByName,
            Program program) {
        Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName = new HashMap<>();

        if (trialByName.size() > 0 &&
                trialByName.values().iterator().next().getBrAPIObject().getAdditionalInfo().has(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID)) {
            String datasetId = trialByName.values().iterator().next().getBrAPIObject()
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
                processAndCacheObsVarDataset(dataSetDetails, obsVarDatasetByName);
            } catch (ApiException e) {
                log.error(Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            }
        }
        return obsVarDatasetByName;
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
            processAndCacheObsVarDataset(dataSetDetails, obsVarDatasetByName);
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

    private void processAndCacheObsVarDataset(BrAPIListDetails existingList, Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName) {
        BrAPIExternalReference xref = Utilities.getExternalReference(existingList.getExternalReferences(),
                        String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.DATASET.getName()))
                .orElseThrow(() -> new IllegalStateException("External references wasn't found for list (dbid): " + existingList.getListDbId()));
        obsVarDatasetByName.put(existingList.getListName(),
                new PendingImportObject<BrAPIListDetails>(ImportObjectState.EXISTING, existingList, UUID.fromString(xref.getReferenceId())));
    }

    /**
     * Initializes a mapping of BrAPI Germplasm objects by Germplasm ID for existing BrAPI Observation Units.
     * This method retrieves existing Germplasms associated with the provided Observation Units and creates a mapping
     * using their Accession Number as the key and a PendingImportObject containing the Germplasm object and a reference ID.
     * If no existing Germplasms are found, an empty mapping is returned.
     *
     * @param unitByName A mapping of Observation Units by name.
     * @param program The BrAPI Program object to which the Germplasms belong.
     * @return A mapping of BrAPI Germplasm objects by Germplasm ID for existing Observation Units.
     * @throws InternalServerException If an error occurs while fetching Germplasms from the database.
     */
    private Map<String, PendingImportObject<BrAPIGermplasm>> initializeGermplasmByGIDForExistingObservationUnits(
            Map<String, PendingImportObject<BrAPIObservationUnit>> unitByName,
            Program program) {
        Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = new HashMap<>();

        List<BrAPIGermplasm> existingGermplasms = new ArrayList<>();
        if(unitByName.size() > 0) {
            Set<String> germplasmDbIds = unitByName.values().stream().map(ou -> ou.getBrAPIObject().getGermplasmDbId()).collect(Collectors.toSet());
            try {
                existingGermplasms.addAll(brAPIGermplasmDAO.getGermplasmsByDBID(germplasmDbIds, program.getId()));
            } catch (ApiException e) {
                log.error("Error fetching germplasm: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            }
        }

        existingGermplasms.forEach(existingGermplasm -> {
            BrAPIExternalReference xref = Utilities.getExternalReference(existingGermplasm.getExternalReferences(), String.format("%s", BRAPI_REFERENCE_SOURCE))
                    .orElseThrow(() -> new IllegalStateException("External references wasn't found for germplasm (dbid): " + existingGermplasm.getGermplasmDbId()));
            existingGermplasmByGID.put(existingGermplasm.getAccessionNumber(), new PendingImportObject<>(ImportObjectState.EXISTING, existingGermplasm, UUID.fromString(xref.getReferenceId())));
        });
        return existingGermplasmByGID;
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
            existingGermplasmByGID.put(existingGermplasm.getAccessionNumber(), new PendingImportObject<>(ImportObjectState.EXISTING, existingGermplasm, UUID.fromString(xref.getReferenceId())));
        });
        return existingGermplasmByGID;
    }

    private void processAndCacheObservationUnit(BrAPIObservationUnit brAPIObservationUnit, String refSource, Program program, Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByName,
                                                Map<String, ExperimentObservation> rowByObsUnitId) {
        BrAPIExternalReference idRef = Utilities.getExternalReference(brAPIObservationUnit.getExternalReferences(), refSource)
                                                .orElseThrow(() -> new InternalServerException("An ObservationUnit ID was not found in any of the external references"));

        ExperimentObservation row = rowByObsUnitId.get(idRef.getReferenceId());
        row.setExpUnitId(Utilities.removeProgramKeyAndUnknownAdditionalData(brAPIObservationUnit.getObservationUnitName(), program.getKey()));
        observationUnitByName.put(createObservationUnitKey(row),
                new PendingImportObject<>(ImportObjectState.EXISTING,
                                          brAPIObservationUnit,
                                          UUID.fromString(idRef.getReferenceId())));
    }
    private PendingImportObject<BrAPIStudy> processAndCacheStudy(
            BrAPIStudy existingStudy,
            Program program,
            Function<BrAPIStudy, String> getterFunction,
            Map<String, PendingImportObject<BrAPIStudy>> studyMap) throws Exception {
        PendingImportObject<BrAPIStudy> pendingStudy;
        BrAPIExternalReference xref = Utilities.getExternalReference(existingStudy.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.STUDIES.getName()))
                                               .orElseThrow(() -> new IllegalStateException("External references wasn't found for study (dbid): " + existingStudy.getStudyDbId()));
        // map season dbid to year
        String seasonDbId = existingStudy.getSeasons().get(0); // It is assumed that the study has only one season
        if(StringUtils.isNotBlank(seasonDbId)) {
            String seasonYear = this.seasonDbIdToYear(seasonDbId, program.getId());
            existingStudy.setSeasons(Collections.singletonList(seasonYear));
        }
        pendingStudy = new PendingImportObject<>(
                ImportObjectState.EXISTING,
                (BrAPIStudy) Utilities.formatBrapiObjForDisplay(existingStudy, BrAPIStudy.class, program),
                UUID.fromString(xref.getReferenceId())
        );
        studyMap.put(
                Utilities.removeProgramKeyAndUnknownAdditionalData(getterFunction.apply(existingStudy), program.getKey()),
                pendingStudy
        );
        return pendingStudy;
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

                trials.forEach(trial -> processAndCacheTrial(trial, program, trialByName));
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

    private void processAndCacheTrial(
        BrAPITrial existingTrial, 
        Program program,
        Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope) {

        //get TrialId from existingTrial
        BrAPIExternalReference experimentIDRef = Utilities.getExternalReference(existingTrial.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName()))
                                                          .orElseThrow(() -> new InternalServerException("An Experiment ID was not found in any of the external references"));
        UUID experimentId = UUID.fromString(experimentIDRef.getReferenceId());

        trialByNameNoScope.put(
                Utilities.removeProgramKey(existingTrial.getTrialName(), program.getKey()),
                new PendingImportObject<>(ImportObjectState.EXISTING, existingTrial, experimentId));
    }

    /**
     * This function collates unique ObsUnitID values from a list of BrAPIImport objects.
     * It iterates through the list and adds non-blank ObsUnitID values to a Set. It also checks for any repeated ObsUnitIDs.
     *
     * @param importRows a List of BrAPIImport objects containing ExperimentObservation data
     * @return a Set of unique ObsUnitID strings
     * @throws IllegalStateException if a repeated ObsUnitID is encountered
     */
    private Set<String> collateReferenceOUIds(List<BrAPIImport> importRows) {
        Set<String> referenceOUIds = new HashSet<>();
        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);

            // Check if ObsUnitID is blank
            if (importRow.getObsUnitID() == null || importRow.getObsUnitID().isBlank()) {
                hasAllReferenceUnitIds = false;
            } else if (referenceOUIds.contains(importRow.getObsUnitID())) {
                // Throw exception if ObsUnitID is repeated
                throw new IllegalStateException("ObsUnitId is repeated: " + importRow.getObsUnitID());
            } else {
                // Add ObsUnitID to referenceOUIds
                referenceOUIds.add(importRow.getObsUnitID());
                hasNoReferenceUnitIds = false;
            }
        }
        return referenceOUIds;
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
                Integer intYear = null;
                if( StringUtils.isNotBlank(year) ){
                    intYear = Integer.parseInt(year);
                }
                newSeason.setYear(intYear);
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

    /**
     * Returns the single value from the given map, throwing an UnprocessableEntityException if the map does not contain exactly one entry.
     *
     * @param map The map from which to retrieve the single value.
     * @param message The error message to include in the UnprocessableEntityException if the map does not contain exactly one entry.
     * @return The single value from the map.
     * @throws UnprocessableEntityException if the map does not contain exactly one entry.
     */
    private <K, V> V getSingleEntryValue(Map<K, V> map, String message) throws UnprocessableEntityException {
        if (map.size() != 1) {
            throw new UnprocessableEntityException(message);
        }
        return map.values().iterator().next();
    }
}
