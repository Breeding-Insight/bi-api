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

package org.breedinginsight.brapps.importer.services.processors.experiment.create.workflow;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.model.workflow.ImportContext;
import org.breedinginsight.brapps.importer.model.workflow.ProcessedData;
import org.breedinginsight.brapps.importer.model.workflow.Workflow;
import org.breedinginsight.brapps.importer.services.ImportStatusService;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.ProcessContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.ProcessedPhenotypeData;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.workflow.steps.CommitPendingImportObjectsStep;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.workflow.steps.PopulateExistingPendingImportObjectsStep;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.workflow.steps.PopulateNewPendingImportObjectsStep;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.workflow.steps.ValidatePendingImportObjectsStep;
import org.breedinginsight.brapps.importer.services.processors.experiment.services.ExperimentPhenotypeService;
import org.breedinginsight.services.exceptions.ValidatorException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflowResult;
import org.breedinginsight.brapps.importer.model.workflow.ExperimentWorkflow;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentWorkflowNavigator;

import javax.inject.Singleton;
import java.util.Optional;

@Slf4j
@Getter
@Singleton
public class CreateNewExperimentWorkflow implements ExperimentWorkflow {
    private final ExperimentWorkflowNavigator.Workflow workflow;
    private final PopulateExistingPendingImportObjectsStep populateExistingPendingImportObjectsStep;
    private final PopulateNewPendingImportObjectsStep populateNewPendingImportObjectsStep;
    private final CommitPendingImportObjectsStep commitPendingImportObjectsStep;
    private final ValidatePendingImportObjectsStep validatePendingImportObjectsStep;
    private final ImportStatusService statusService;
    private final ExperimentPhenotypeService experimentPhenotypeService;

    @Inject
    public CreateNewExperimentWorkflow(PopulateExistingPendingImportObjectsStep populateExistingPendingImportObjectsStep,
                                       PopulateNewPendingImportObjectsStep populateNewPendingImportObjectsStep,
                                       CommitPendingImportObjectsStep commitPendingImportObjectsStep,
                                       ValidatePendingImportObjectsStep validatePendingImportObjectsStep,
                                       ImportStatusService statusService,
                                       ExperimentPhenotypeService experimentPhenotypeService) {
        this.populateExistingPendingImportObjectsStep = populateExistingPendingImportObjectsStep;
        this.populateNewPendingImportObjectsStep = populateNewPendingImportObjectsStep;
        this.commitPendingImportObjectsStep = commitPendingImportObjectsStep;
        this.validatePendingImportObjectsStep = validatePendingImportObjectsStep;
        this.statusService = statusService;
        this.experimentPhenotypeService = experimentPhenotypeService;
        this.workflow = ExperimentWorkflowNavigator.Workflow.NEW_OBSERVATION;
    }

    private ImportPreviewResponse runWorkflow(ImportContext context) throws Exception {

        ImportUpload upload = context.getUpload();
        boolean commit = context.isCommit();
        List<BrAPIImport> importRows = context.getImportRows();

        // Make sure the file does not contain obs unit ids before proceeding
        if (containsObsUnitIDs(context)) {
            // TODO: get file name
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Error detected in file, " +
                    upload.getUploadFileName() + ". ObsUnitIDs are detected. Import cannot proceed");
        }

        statusService.updateMessage(upload, "Checking existing experiment objects in brapi service and mapping data");

        ProcessedPhenotypeData phenotypeData = experimentPhenotypeService.extractPhenotypes(context);
        ProcessContext processContext = populateExistingPendingImportObjectsStep.process(context, phenotypeData);
        ProcessedData processedData = populateNewPendingImportObjectsStep.process(processContext, phenotypeData);
        ValidationErrors validationErrors = validatePendingImportObjectsStep.process(context, processContext.getPendingData(), phenotypeData, processedData);

        // short circuit if there were validation errors
        if (validationErrors.hasErrors()) {
            throw new ValidatorException(validationErrors);
        }

        // TODO: move to experiment import service
        ImportPreviewResponse response = buildImportPreviewResponse(importRows, processContext.getPendingData(), processedData, upload);

        statusService.updateMappedData(upload, response, "Finished mapping data to brapi objects");

        // preview data
        if (!commit) {
            statusService.updateOk(upload);
            return response;
        }

        // commit data
        long totalObjects = getNewObjectCount(response);
        statusService.startUpload(upload, totalObjects, "Starting upload to brapi service");
        statusService.updateMessage(upload, "Creating new experiment objects in brapi service");

        commitPendingImportObjectsStep.process(processContext, processedData);

        statusService.finishUpload(upload, totalObjects, "Completed upload to brapi service");
        return response;
    }

    /**
     * Retrieves the name of the workflow. This is used for logging display purposes.
     *
     * @return the name of the workflow
     */
    public Optional<ImportWorkflowResult> process(ImportServiceContext context) throws Exception {
        // Workflow processing the context
        ImportWorkflow workflow = ImportWorkflow.builder()
                .id(getWorkflow().getId())
                .name(getWorkflow().getName())
                .build();

        // No-preview result
        Optional<ImportWorkflowResult> result;

        result = Optional.of(ImportWorkflowResult.builder()
                .workflow(workflow)
                .importPreviewResponse(Optional.empty())
                .build());

        // Skip this workflow unless creating a new experiment
        if (context != null && !this.workflow.isEqual(context.getWorkflow())) {
            return Optional.empty();
        }

        // Skip processing if no context, but return no-preview result for this workflow
        if (context == null) {
            return result;
        }

        // TODO: unify usage of single import context type throughout
        ImportContext importContext = ImportContext.from(context);

        // Start processing the import...
        ImportPreviewResponse response = runWorkflow(importContext);

        result = Optional.of(ImportWorkflowResult.builder()
                .workflow(workflow)
                .importPreviewResponse(Optional.of(response))
                .build());

        return result;
    }

    @Override
    public int getOrder() {
        return 1;
    }

    // TODO: move to shared area
    private ImportPreviewResponse buildImportPreviewResponse(List<BrAPIImport> importRows, PendingData pendingData, ProcessedData processedData,
                                                             ImportUpload upload) {

        Map<Integer, PendingImport> mappedBrAPIImport = processedData.getMappedBrAPIImport();
        Map<String, ImportPreviewStatistics> statistics = generateStatisticsMap(pendingData, importRows);

        ImportPreviewResponse response = new ImportPreviewResponse();
        response.setStatistics(statistics);
        List<PendingImport> mappedBrAPIImportList = new ArrayList<>(mappedBrAPIImport.values());
        response.setRows(mappedBrAPIImportList);
        response.setDynamicColumnNames(upload.getDynamicColumnNamesList());
        return response;
    }

    // TODO: move to shared area
    private long getNewObjectCount(ImportPreviewResponse response) {
        // get total number of new brapi objects to create
        long totalObjects = 0;
        for (ImportPreviewStatistics stats : response.getStatistics().values()) {
            totalObjects += stats.getNewObjectCount();
        }
        return totalObjects;
    }

    private boolean containsObsUnitIDs(ImportContext importContext) {
        List<BrAPIImport> importRows = importContext.getImportRows();
        return importRows.stream()
                .anyMatch(row -> {
                    ExperimentObservation expRow = (ExperimentObservation) row;
                    return StringUtils.isNotBlank(expRow.getObsUnitID());
                });
    }

    // TODO: move to shared area: experiment import service
    private Map<String, ImportPreviewStatistics> generateStatisticsMap(PendingData pendingData, List<BrAPIImport> importRows) {
        // Data for stats.
        HashSet<String> environmentNameCounter = new HashSet<>(); // set of unique environment names
        HashSet<String> obsUnitsIDCounter = new HashSet<>(); // set of unique observation unit ID's
        HashSet<String> gidCounter = new HashSet<>(); // set of unique GID's

        Map<String, PendingImportObject<BrAPIObservation>> observationByHash = pendingData.getObservationByHash();

        for (BrAPIImport row : importRows) {
            ExperimentObservation importRow = (ExperimentObservation) row;
            // Collect date for stats.
            addIfNotNull(environmentNameCounter, importRow.getEnv());
            addIfNotNull(obsUnitsIDCounter, ExperimentUtilities.createObservationUnitKey(importRow));
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
                observationByHash.values()
                        .stream()
                        .filter(preview -> preview != null && preview.getState() == ImportObjectState.EXISTING &&
                                !StringUtils.isBlank(preview.getBrAPIObject()
                                        .getValue()))
                        .count()
        );

        int numMutatedObservations = Math.toIntExact(
                observationByHash.values()
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

    // TODO: move to common area
    private void addIfNotNull(HashSet<String> set, String setValue) {
        if (setValue != null) {
            set.add(setValue);
        }
    }
}

