package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.brapi;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.micronaut.http.HttpStatus;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;
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
import org.breedinginsight.brapps.importer.model.imports.ChangeLogEntry;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.FileMappingUtil;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.InitialData;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.OverwrittenData;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.UnchangedData;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.VisitedObservationData;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationService;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationVariableService;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.StudyService;
import org.breedinginsight.dao.db.tables.pojos.TraitEntity;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.utilities.Utilities;
import org.jooq.impl.QOM;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.*;
import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.ErrMessage.MULTIPLE_EXP_TITLES;

@Slf4j
public class PendingObservation extends ExpUnitMiddleware {
    StudyService studyService;
    ObservationVariableService observationVariableService;
    ObservationService observationService;
    BrAPIObservationDAO brAPIObservationDAO;
    FileMappingUtil fileMappingUtil;
    Gson gson;

    @Inject
    public PendingObservation(StudyService studyService,
                              ObservationVariableService observationVariableService,
                              BrAPIObservationDAO brAPIObservationDAO,
                              ObservationService observationService,
                              FileMappingUtil fileMappingUtil,
                              Gson gson) {
        this.studyService = studyService;
        this.observationVariableService = observationVariableService;
        this.brAPIObservationDAO = brAPIObservationDAO;
        this.observationService = observationService;
        this.fileMappingUtil = fileMappingUtil;
        this.gson = gson;
    }

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        log.debug("verifying traits listed in import");

        // Get all the dynamic columns of the import
        ImportUpload upload = context.getImportContext().getUpload();
        Table data = context.getImportContext().getData();
        String[] dynamicColNames = upload.getDynamicColumnNames();
        List<Column<?>> dynamicCols = data.columns(dynamicColNames);

        // Collect the columns for observation variable data
        List<Column<?>> phenotypeCols = dynamicCols.stream().filter(col -> !col.name().startsWith(TIMESTAMP_PREFIX)).collect(Collectors.toList());
        Set<String> varNames = phenotypeCols.stream().map(Column::name).collect(Collectors.toSet());

        // Collect the columns for observation timestamps
        List<Column<?>> timestampCols = dynamicCols.stream().filter(col -> col.name().startsWith(TIMESTAMP_PREFIX)).collect(Collectors.toList());
        Set<String> tsNames = timestampCols.stream().map(Column::name).collect(Collectors.toSet());

        // Construct validation errors for any timestamp columns that don't have a matching variable column
        List<BrAPIImport> importRows = context.getImportContext().getImportRows();
        ValidationErrors validationErrors = context.getPendingData().getValidationErrors();
        List<ValidationError> tsValErrs = observationVariableService.validateMatchedTimestamps(varNames, timestampCols).orElse(new ArrayList<>());
        for (int i = 0; i < importRows.size(); i++) {
            int rowNum = i;
            tsValErrs.forEach(validationError -> validationErrors.addError(rowNum, validationError));
        }

        // Stop processing the import if there are unmatched timestamp columns
        if (tsValErrs.size() > 0) {
            this.compensate(context, new MiddlewareError(() -> {
                // any handling...
            }));
        }

        //Now know timestamps all valid phenotypes, can associate with phenotype column name for easy retrieval
        Map<String, Column<?>> tsColByPheno = timestampCols.stream().collect(Collectors.toMap(col -> col.name().replaceFirst(TIMESTAMP_REGEX, StringUtils.EMPTY), col -> col));

        // Add the map to the context for use in processing import
        context.getPendingData().setTimeStampColByPheno(tsColByPheno);

        try {
            // Fetch the traits named in the observation variable columns
            Program program = context.getImportContext().getProgram();
            List<Trait> traits = observationVariableService.fetchTraitsByName(varNames, program);

            // Sort the traits to match the order of the headers in the import file
            List<Trait> sortedTraits = fileMappingUtil.sortByField(List.copyOf(varNames), new ArrayList<>(traits), TraitEntity::getObservationVariableName);

            // Get the pending observation dataset
            PendingImportObject<BrAPITrial> pendingTrial = ExperimentUtilities.getSingleEntryValue(context.getPendingData().getTrialByNameNoScope()).orElseThrow(()->new UnprocessableEntityException(MULTIPLE_EXP_TITLES.getValue()));
            String datasetName = String.format("Observation Dataset [%s-%s]", program.getKey(), pendingTrial.getBrAPIObject().getAdditionalInfo().get(BrAPIAdditionalInfoFields.EXPERIMENT_NUMBER).getAsString());
            PendingImportObject<BrAPIListDetails> pendingDataset = context.getPendingData().getObsVarDatasetByName().get(datasetName);

            // Add new phenotypes to the pending observation dataset list (NOTE: "obsVarName [programKey]" is used instead of obsVarDbId)
            // TODO: Change to using actual dbIds as per the BrAPI spec, instead of namespaced obsVar names (necessary for Breedbase)
            Set<String> datasetObsVarDbIds = pendingDataset.getBrAPIObject().getData().stream().collect(Collectors.toSet());
            Set<String> phenoDbIds = sortedTraits.stream().map(t->Utilities.appendProgramKey(t.getObservationVariableName(), program.getKey())).collect(Collectors.toSet());
            phenoDbIds.removeAll(datasetObsVarDbIds);
            pendingDataset.getBrAPIObject().getData().addAll(phenoDbIds);

            // Update pending status
            if (ImportObjectState.EXISTING == pendingDataset.getState()) {
                pendingDataset.setState(ImportObjectState.MUTATED);
            }

            // Read any observation data stored for these traits
            log.debug("fetching observation data stored for traits");
            Set<String> ouDbIds = context.getExpUnitContext().getPendingObsUnitByOUId().values().stream().map(u -> u.getBrAPIObject().getObservationUnitDbId()).collect(Collectors.toSet());
            Set<String> varDbIds = sortedTraits.stream().map(t->t.getObservationVariableDbId()).collect(Collectors.toSet());
            List<BrAPIObservation> observations = brAPIObservationDAO.getObservationsByObservationUnitsAndVariables(ouDbIds, varDbIds, program);

            // Construct helper lookup tables to use for hashing stored observation data
            Map<String, String> unitNameByDbId = context.getExpUnitContext().getPendingObsUnitByOUId().values().stream().map(PendingImportObject::getBrAPIObject).collect(Collectors.toMap(BrAPIObservationUnit::getObservationUnitDbId, BrAPIObservationUnit::getObservationUnitName));
            Map<String, String> variableNameByDbId = sortedTraits.stream().collect(Collectors.toMap(Trait::getObservationVariableDbId, Trait::getObservationVariableName));
            Map<String, String> studyNameByDbId = context.getPendingData().getStudyByNameNoScope().values().stream()
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
            context.getPendingData().setExistingObsByObsHash(observationByObsHash);

            // Build new pending observation data for each phenotype
            Map<String, PendingImportObject<BrAPIObservation>> pendingObservationByHash = new HashMap<>();


            // Checking all import rows for data
            for (int i = 0; i < context.getImportContext().getImportRows().size(); i++) {
                Integer rowNum = i;
                ExperimentObservation row = (ExperimentObservation) context.getImportContext().getImportRows().get(rowNum);

                // Construct the pending import for the row
                PendingImport mappedImportRow = context.getImportContext().getMappedBrAPIImport().getOrDefault(rowNum, new PendingImport());
                String unitId = row.getObsUnitID();
                mappedImportRow.setTrial(context.getExpUnitContext().getPendingTrialByOUId().get(unitId));
                mappedImportRow.setLocation(context.getExpUnitContext().getPendingLocationByOUId().get(unitId));
                mappedImportRow.setStudy(context.getExpUnitContext().getPendingStudyByOUId().get(unitId));
                mappedImportRow.setObservationUnit(context.getExpUnitContext().getPendingObsUnitByOUId().get(unitId));
                mappedImportRow.setGermplasm(context.getExpUnitContext().getPendingGermplasmByOUId().get(unitId));

                // For each phenotype, construct the pending observations
                for (Column<?> column : phenotypeCols) {
                    String cellData = column.getString(rowNum);

                    // Generate hash for looking up prior observation data
                    String studyName = context.getExpUnitContext().getPendingStudyByOUId().get(unitId).getBrAPIObject().getStudyName();
                    String unitName = context.getExpUnitContext().getPendingObsUnitByOUId().get(unitId).getBrAPIObject().getObservationUnitName();
                    String phenoColumnName = column.name();
                    String observationHash = observationService.getObservationHash(unitName, phenoColumnName, studyName);

                    // Get timestamp if associated column
                    String timestamp = null;
                    String tsColumnName = null;
                    if (tsColByPheno.containsKey(phenoColumnName)) {
                        timestamp = tsColByPheno.get(phenoColumnName).getString(rowNum);
                        tsColumnName = tsColByPheno.get(phenoColumnName).name();

                        // If timestamp is not valid, set to midnight
                        if (timestamp != null && !timestamp.isBlank() && (!observationService.validDateTimeValue(timestamp) || !observationService.validDateValue(timestamp))) {
                            timestamp += MIDNIGHT;

                            // Add a validation error
                            ValidationError timestampValErr = new ValidationError(tsColByPheno.get(phenoColumnName).name(), String.format("Timestamp format is not valid for %s", tsColByPheno.get(phenoColumnName).name()), HttpStatus.UNPROCESSABLE_ENTITY);
                            validationErrors.addError(rowNum, timestampValErr);
                        }
                    }

                    VisitedObservationData processedData = null;
                    // Is there prior observation data for this unit + var?
                    if (observationByObsHash.containsKey(observationHash)) {

                        // Clone the prior observation
                        BrAPIObservation observation = gson.fromJson(gson.toJson(observationByObsHash.get(observationHash)), BrAPIObservation.class);

                        // Is there a change to the prior data?
                        if (!cellData.equals(observation.getValue()) || (timestamp != null && !OffsetDateTime.parse(timestamp).equals(observation.getObservationTimeStamp()))) {

                            // Is prior data protected?
                            boolean canOverwrite = context.getImportContext().isCommit() && "false".equals( row.getOverwrite() == null ? "false" : row.getOverwrite());

                            // create new instance of OverwrittenData
                            processedData = new OverwrittenData(canOverwrite,
                                    context.getImportContext().isCommit(),
                                    unitId,
                                    phenoColumnName,
                                    tsColumnName,
                                    cellData,
                                    timestamp,
                                    Optional.ofNullable(row.getOverwriteReason()).orElse(""),
                                    observation,
                                    context.getImportContext().getUser().getId(),
                                    program);
                        } else {

                            // create new instance of UnchangedData
                            processedData = new UnchangedData(observation, program);
                        }

                    } else {

                        // Clone the observation unit
                        BrAPIObservationUnit observationUnit = gson.fromJson(gson.toJson(context.getExpUnitContext().getPendingObsUnitByOUId().get(row.getExpUnitId()).getBrAPIObject()), BrAPIObservationUnit.class);

                        // create new instance of InitialData
                        processedData = new InitialData(context.getImportContext().isCommit(),
                                cellData,
                                phenoColumnName,
                                row,
                                pendingTrial.getId(),
                                context.getExpUnitContext().getPendingStudyByOUId().get(unitId).getId(),
                                unitId,
                                context.getExpUnitContext().getPendingStudyByOUId().get(unitId).getBrAPIObject().getSeasons().get(0),
                                observationUnit,
                                context.getImportContext().getUser(),
                                context.getImportContext().getProgram());
                    }

                    // Validate processed data
                    processedData.getValidationErrors().ifPresent(errList -> errList.forEach(e->validationErrors.addError(rowNum, e)));

                    // Construct a pending observation
                    PendingImportObject<BrAPIObservation> pendingProcessedData = processedData.constructPendingObservation();

                    // Set the new pending observation in the pending import for the row
                    mappedImportRow.getObservations().add(pendingProcessedData);

                    // Add pending observation to map
                    pendingObservationByHash.put(observationHash, pendingProcessedData);
                }

                // Set the pending import for the row
                context.getImportContext().getMappedBrAPIImport().put(rowNum, mappedImportRow);
            }

            // Add the pending observation map to the context for use in processing the import
            context.getPendingData().setPendingObservationByHash(pendingObservationByHash);
        } catch (DoesNotExistException | ApiException | UnprocessableEntityException e) {
            this.compensate(context, new MiddlewareError(() -> {
                throw new RuntimeException(e);
            }));
        }

        return processNext(context);
    }
}
