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

package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process;

import com.github.filosganga.geogson.gson.GeometryAdapterFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.data.ProcessedDataFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.data.VisitedObservationData;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.MiddlewareException;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationService;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationVariableService;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.StudyService;
import org.breedinginsight.brapps.importer.services.processors.experiment.validator.field.FieldValidator;
import org.breedinginsight.dao.db.tables.pojos.TraitEntity;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.utilities.Utilities;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.ErrMessage.MULTIPLE_EXP_TITLES;
import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.TIMESTAMP_PREFIX;
import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.TIMESTAMP_REGEX;

@Slf4j
@Prototype
public class ImportTableProcess extends AppendOverwriteMiddleware {
    @Property(name = "brapi.server.reference-source")
    String brapiReferenceSource;
    StudyService studyService;
    ObservationVariableService observationVariableService;
    ObservationService observationService;
    BrAPIObservationDAO brAPIObservationDAO;
    ExperimentUtilities experimentUtil;
    Gson gson;
    FieldValidator fieldValidator;
    AppendStatistic statistic;
    ProcessedDataFactory processedDataFactory;

    @Inject
    public ImportTableProcess(StudyService studyService,
                              ObservationVariableService observationVariableService,
                              BrAPIObservationDAO brAPIObservationDAO,
                              ObservationService observationService,
                              ExperimentUtilities experimentUtil,
                              FieldValidator fieldValidator,
                              AppendStatistic statistic,
                              ProcessedDataFactory processedDataFactory) {
        this.studyService = studyService;
        this.observationVariableService = observationVariableService;
        this.brAPIObservationDAO = brAPIObservationDAO;
        this.observationService = observationService;
        this.experimentUtil = experimentUtil;
        this.gson = new GsonBuilder().registerTypeAdapterFactory(new GeometryAdapterFactory()).create();
        this.fieldValidator = fieldValidator;
        this.statistic = statistic;
        this.processedDataFactory = processedDataFactory;
    }

    @Override
    public AppendOverwriteMiddlewareContext process(AppendOverwriteMiddlewareContext context) {
        log.debug("verifying traits listed in import");

        // Get all the dynamic columns of the import
        ImportUpload upload = context.getImportContext().getUpload();
        Table data = context.getImportContext().getData();
        String[] dynamicColNames = upload.getDynamicColumnNames();

        // don't allow periods (.) or square brackets in Dynamic Column Names
        for (String dynamicColumnName: dynamicColNames) {
            if(dynamicColumnName.contains(".") || dynamicColumnName.contains("[") || dynamicColumnName.contains("]")){
                String errorMsg = String.format("Observation columns may not contain periods or square brackets (see column '%s')", dynamicColumnName);
                throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, errorMsg);
            }
        }
        List<Column<?>> dynamicCols = data.columns(dynamicColNames);

        // Collect the columns for observation variable data
        List<Column<?>> phenotypeCols = dynamicCols.stream().filter(col -> !col.name().startsWith(TIMESTAMP_PREFIX)).collect(Collectors.toList());
        List<String> varNames = phenotypeCols.stream().map(Column::name).collect(Collectors.toList());

        // Collect the columns for observation timestamps
        List<Column<?>> timestampCols = dynamicCols.stream().filter(col -> col.name().startsWith(TIMESTAMP_PREFIX)).collect(Collectors.toList());
        Set<String> tsNames = timestampCols.stream().map(Column::name).collect(Collectors.toSet());

        // Construct validation errors for any timestamp columns that don't have a matching variable column
        List<BrAPIImport> importRows = context.getImportContext().getImportRows();
        Optional.ofNullable(context.getAppendOverwriteWorkflowContext().getValidationErrors()).orElseGet(() -> {
            context.getAppendOverwriteWorkflowContext().setValidationErrors(new ValidationErrors());
            return new ValidationErrors();
        });
        ValidationErrors validationErrors = context.getAppendOverwriteWorkflowContext().getValidationErrors();
        List<ValidationError> tsValErrs = observationVariableService.validateMatchedTimestamps(Set.copyOf(varNames), timestampCols).orElse(new ArrayList<>());
        for (int i = 0; i < importRows.size(); i++) {
            int rowNum = i;
            tsValErrs.forEach(validationError -> validationErrors.addError(rowNum, validationError));
        }

        try {
            // Stop processing the import if there are unmatched timestamp columns
            if (tsValErrs.size() > 0) {
                throw new UnprocessableEntityException("One or more timestamp columns do not have a matching observation variable");
            }

            //Now know timestamps all valid phenotypes, can associate with phenotype column name for easy retrieval
            Map<String, Column<?>> tsColByPheno = timestampCols.stream().collect(Collectors.toMap(col -> col.name().replaceFirst(TIMESTAMP_REGEX, StringUtils.EMPTY), col -> col));

            // Add the map to the context for use in processing import
            context.getAppendOverwriteWorkflowContext().setTimeStampColByPheno(tsColByPheno);

            // Fetch the traits named in the observation variable columns
            Program program = context.getImportContext().getProgram();
            List<Trait> traits = observationVariableService.fetchTraitsByName(Set.copyOf(varNames), program);

            // Map trait by phenotype column name
            Map<String, Trait> traitByPhenoColName = traits.stream().collect(
                    Collectors.toMap(
                            trait -> trait.getObservationVariableName().toUpperCase(),  // Use uppercase keys for case-insensitivity
                            trait -> trait,
                            (trait1, trait2) -> trait1,  // Merge function
                            CaseInsensitiveMap::new  // Supplier for creating a CaseInsensitiveMap
                    )
            );

            // Sort the traits to match the order of the headers in the import file
            List<Trait> sortedTraits = experimentUtil.sortByField(varNames, new ArrayList<>(traits), TraitEntity::getObservationVariableName);

            // Get the pending observation dataset
            PendingImportObject<BrAPITrial> pendingTrial = ExperimentUtilities.getSingleEntryValue(context.getAppendOverwriteWorkflowContext().getTrialByNameNoScope()).orElseThrow(()->new UnprocessableEntityException(MULTIPLE_EXP_TITLES.getValue()));
            String datasetName = String.format("Observation Dataset [%s-%s]", program.getKey(), pendingTrial.getBrAPIObject().getAdditionalInfo().get(BrAPIAdditionalInfoFields.EXPERIMENT_NUMBER).getAsString());
            PendingImportObject<BrAPIListDetails> pendingDataset = context.getAppendOverwriteWorkflowContext().getObsVarDatasetByName().get(datasetName);

            // Add new phenotypes to the pending observation dataset list (NOTE: "obsVarName [programKey]" is used instead of obsVarDbId)
            // TODO: Change to using actual dbIds as per the BrAPI spec, instead of namespaced obsVar names (was necessary for Breedbase)
            List<String> datasetObsVarDbIds = pendingDataset.getBrAPIObject().getData().stream().collect(Collectors.toList());
            List<String> phenoDbIds = sortedTraits.stream().map(t->Utilities.appendProgramKey(t.getObservationVariableName(), program.getKey())).collect(Collectors.toList());
            phenoDbIds.removeAll(datasetObsVarDbIds);
            pendingDataset.getBrAPIObject().getData().addAll(phenoDbIds);

            // Update pending status
            if (ImportObjectState.EXISTING == pendingDataset.getState()) {
                pendingDataset.setState(ImportObjectState.MUTATED);
            }

            // Read any observation data stored for these traits
            log.debug("fetching observation data stored for traits");
            Set<String> ouDbIds = context.getAppendOverwriteWorkflowContext().getPendingObsUnitByOUId().values().stream().map(u -> u.getBrAPIObject().getObservationUnitDbId()).collect(Collectors.toSet());
            Set<String> varDbIds = sortedTraits.stream().map(t->t.getObservationVariableDbId()).collect(Collectors.toSet());
            List<BrAPIObservation> observations = brAPIObservationDAO.getObservationsByObservationUnitsAndVariables(ouDbIds, varDbIds, program);

            // Construct helper lookup tables to use for hashing stored observation data
            Map<String, String> unitNameByDbId = context.getAppendOverwriteWorkflowContext().getPendingObsUnitByOUId().values().stream().map(PendingImportObject::getBrAPIObject).collect(Collectors.toMap(BrAPIObservationUnit::getObservationUnitDbId, BrAPIObservationUnit::getObservationUnitName));
            Map<String, String> variableNameByDbId = sortedTraits.stream().collect(Collectors.toMap(Trait::getObservationVariableDbId, Trait::getObservationVariableName));
            Map<String, String> studyNameByDbId = context.getAppendOverwriteWorkflowContext().getStudyByNameNoScope().values().stream()
                    .filter(pio -> StringUtils.isNotBlank(pio.getBrAPIObject().getStudyDbId()))
                    .map(PendingImportObject::getBrAPIObject)
                    .collect(Collectors.toMap(BrAPIStudy::getStudyDbId, brAPIStudy -> Utilities.removeProgramKeyAndUnknownAdditionalData(brAPIStudy.getStudyName(), program.getKey())));

            // Hash stored observation data using a signature of unit, variable, and study names
            Map<String, BrAPIObservation> observationByObsHash = observations.stream().collect(Collectors.toMap(o->{
                return observationService.getObservationHash(unitNameByDbId.get(o.getObservationUnitDbId()),
                        variableNameByDbId.get(o.getObservationVariableDbId()),
                        studyNameByDbId.get(o.getStudyDbId()));
            }, o->o));

            // Add the observation data map to the context for use in processing import
            context.getAppendOverwriteWorkflowContext().setExistingObsByObsHash(observationByObsHash);

            // Build new pending observation data for each phenotype
            Map<String, PendingImportObject<BrAPIObservation>> pendingObservationByHash = new HashMap<>();

            // In case the user aborted an import, clear any old preview statistics before processing the import
            statistic.clearData();

            // Build pending import data maps for each row
            for (int i = 0; i < context.getImportContext().getImportRows().size(); i++) {
                Integer rowNum = i;
                ExperimentObservation row = (ExperimentObservation) context.getImportContext().getImportRows().get(rowNum);

                // Construct the pending import for the row
                Optional.ofNullable(context.getImportContext().getMappedBrAPIImport()).orElseGet(() -> {
                        context.getImportContext().setMappedBrAPIImport(new HashMap<>());
                        return new HashMap<>();
                });
                PendingImport mappedImportRow = context.getImportContext().getMappedBrAPIImport().getOrDefault(rowNum, new PendingImport());
                String unitId = row.getObsUnitID();
                mappedImportRow.setTrial(context.getAppendOverwriteWorkflowContext().getPendingTrialByOUId().get(unitId));
                mappedImportRow.setLocation(context.getAppendOverwriteWorkflowContext().getPendingLocationByOUId().get(unitId));
                mappedImportRow.setStudy(context.getAppendOverwriteWorkflowContext().getPendingStudyByOUId().get(unitId));
                mappedImportRow.setObservationUnit(context.getAppendOverwriteWorkflowContext().getPendingObsUnitByOUId().get(unitId));
                mappedImportRow.setGermplasm(context.getAppendOverwriteWorkflowContext().getPendingGermplasmByOUId().get(unitId));

                // Assemble the pending observation data for all phenotypes
                for (Column<?> column : phenotypeCols) {
                    String cellData = column.getString(rowNum);

                    // Generate hash for looking up prior observation data
                    String studyName = context.getAppendOverwriteWorkflowContext().getPendingStudyByOUId().get(unitId).getBrAPIObject().getStudyName();
                    String unitName = context.getAppendOverwriteWorkflowContext().getPendingObsUnitByOUId().get(unitId).getBrAPIObject().getObservationUnitName();
                    String phenoColumnName = column.name();
                    String observationHash = observationService.getObservationHash(unitName, phenoColumnName, studyName);

                    // Get timestamp if associated column
                    var cell = new Object() {   // mutable reference object to make timestamp accessible in anonymous methods
                        String timestamp = null;
                    };
                    String tsColumnName = null;
                    if (tsColByPheno.containsKey(phenoColumnName)) {
                        cell.timestamp = tsColByPheno.get(phenoColumnName).getString(rowNum);
                        tsColumnName = tsColByPheno.get(phenoColumnName).name();

                        // If timestamp is not valid, add a validation error
                        fieldValidator.validateField(tsColumnName, cell.timestamp, null).ifPresent(err -> {
                            cell.timestamp = null;
                            validationErrors.addError(rowNum + 2, err); // +2 because of excel header row and 1-based row index
                        });

                    }

                    VisitedObservationData processedData = null;

                    // Is there prior observation data for this unit + var?
                    if (observationByObsHash.containsKey(observationHash)) {

                        // Clone the prior observation
                        BrAPIObservation observation = gson.fromJson(gson.toJson(observationByObsHash.get(observationHash)), BrAPIObservation.class);

                        // Is there a change to the prior data?
                        if (
                                isChanged(cellData, observation, cell.timestamp)
                        ) {

                            // Is prior data protected?
                            /**
                             * For preview purposes all data can be treated as overwritable, but data cannot be
                             * overwritten if changes are to be committed and the user has not chosen to overwrite
                              */
                            boolean canOverwrite = !context.getImportContext().isCommit() || !"false".equals(row.getOverwrite() == null ? "false" : row.getOverwrite());

                            // Clone the trait
                            Trait changeTrait = gson.fromJson(gson.toJson(traitByPhenoColName.get(phenoColumnName)), Trait.class);

                            // Create new instance of OverwrittenData
                            processedData = processedDataFactory.overwrittenDataBean(canOverwrite,
                                    context.getImportContext().isCommit(),
                                    unitId,
                                    changeTrait,
                                    phenoColumnName,
                                    tsColumnName,
                                    cellData,
                                    cell.timestamp,
                                    Optional.ofNullable(row.getOverwriteReason()).orElse(""),
                                    observation,
                                    context.getImportContext().getUser().getId(),
                                    program);
                        } else {

                            // create new instance of UnchangedData
                            processedData = processedDataFactory.unchangedDataBean(observation, program);
                        }

                        //
                    } else if (!cellData.isBlank()) {

                        // Clone the observation unit and trait
                        BrAPIObservationUnit observationUnit = gson.fromJson(gson.toJson(context.getAppendOverwriteWorkflowContext().getPendingObsUnitByOUId().get(row.getObsUnitID()).getBrAPIObject()), BrAPIObservationUnit.class);
                        Trait initialTrait = gson.fromJson(gson.toJson(traitByPhenoColName.get(phenoColumnName)), Trait.class);

                        // create new instance of InitialData
                        processedData = processedDataFactory.initialDataBean(brapiReferenceSource,
                                context.getImportContext().isCommit(),
                                context.getAppendOverwriteWorkflowContext().getPendingGermplasmByOUId().get(unitId).getBrAPIObject().getGermplasmName(),
                                context.getAppendOverwriteWorkflowContext().getPendingStudyByOUId().get(unitId).getBrAPIObject(),
                                cellData,
                                cell.timestamp,
                                phenoColumnName,
                                tsColumnName,
                                initialTrait,
                                row,
                                pendingTrial.getId(),
                                context.getAppendOverwriteWorkflowContext().getPendingStudyByOUId().get(unitId).getId(),
                                UUID.fromString(unitId),
                                context.getAppendOverwriteWorkflowContext().getPendingStudyByOUId().get(unitId).getBrAPIObject().getSeasons().get(0),
                                observationUnit,
                                context.getImportContext().getUser(),
                                context.getImportContext().getProgram());
                    } else {
                        // Clone the observation unit
                        BrAPIObservationUnit observationUnit = gson.fromJson(gson.toJson(context.getAppendOverwriteWorkflowContext().getPendingObsUnitByOUId().get(row.getObsUnitID()).getBrAPIObject()), BrAPIObservationUnit.class);

                        processedData = processedDataFactory.emptyDataBean(brapiReferenceSource,
                                context.getImportContext().isCommit(),
                                context.getAppendOverwriteWorkflowContext().getPendingGermplasmByOUId().get(unitId).getBrAPIObject().getGermplasmName(),
                                context.getAppendOverwriteWorkflowContext().getPendingStudyByOUId().get(unitId).getBrAPIObject(),
                                phenoColumnName,
                                pendingTrial.getId(),
                                context.getAppendOverwriteWorkflowContext().getPendingStudyByOUId().get(unitId).getId(),
                                UUID.fromString(unitId),
                                context.getAppendOverwriteWorkflowContext().getPendingStudyByOUId().get(unitId).getBrAPIObject().getSeasons().get(0),
                                observationUnit,
                                context.getImportContext().getUser(),
                                context.getImportContext().getProgram()
                        );
                    }

                    // Validate processed data
                    processedData.getValidationErrors().ifPresent(errList -> errList.forEach(e -> validationErrors.addError(rowNum + 2, e)));  // +2 to account for header row and excel file 1-based row index

                    // Update import preview statistics and set in the context
                    processedData.updateTally(statistic);
                    statistic.addEnvironmentName(studyName);
                    // TODO: change null values to actual data
                    // TODO: change signature to take two args, studyName and unitName
                    statistic.addObservationUnitId(null);
                    statistic.addGid(context.getAppendOverwriteWorkflowContext().getPendingGermplasmByOUId().get(unitId).getBrAPIObject().getAccessionNumber());
                    context.getAppendOverwriteWorkflowContext().setStatistic(statistic);

                    // Construct a pending observation
                    Optional<PendingImportObject<BrAPIObservation>> pendingProcessedData = Optional.ofNullable(processedData.constructPendingObservation());

                    // Set the new pending observation in the pending import for the row
                    pendingProcessedData.ifPresent(observation -> mappedImportRow.getObservations().add(observation));

                    // Add pending observation to map
                    pendingProcessedData.ifPresent(observation -> pendingObservationByHash.put(observationHash, observation));
                }

                // Set the pending import for the row
                context.getImportContext().getMappedBrAPIImport().put(rowNum, mappedImportRow);
            }

            // Throw the total list of all validation errors for the import
            if (validationErrors.hasErrors()) {
                throw new ValidatorException(validationErrors);
            }

            // Add the pending observation map to the context for use in processing the import
            context.getAppendOverwriteWorkflowContext().setPendingObservationByHash(pendingObservationByHash);

            return processNext(context);
        } catch (DoesNotExistException | ApiException | UnprocessableEntityException | ValidatorException | IllegalStateException e) {
            context.getAppendOverwriteWorkflowContext().setProcessError(new MiddlewareException(e));
            return this.compensate(context);
        }
    }

    private boolean isChanged(String cellData, BrAPIObservation observation, String newTimestamp) {
        if (!cellData.isBlank() && !cellData.equals(observation.getValue())){
            return true;
        }
        if (StringUtils.isBlank(newTimestamp)) {
            return (observation.getObservationTimeStamp()!=null);
        }
        return !observationService.parseDateTime(newTimestamp).equals(observation.getObservationTimeStamp());
    }
}
