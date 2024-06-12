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
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.workflow.ImportContext;
import org.breedinginsight.brapps.importer.model.workflow.ProcessedData;
import org.breedinginsight.brapps.importer.model.workflow.Workflow;
import org.breedinginsight.brapps.importer.services.ImportStatusService;
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
import java.util.List;
import java.util.Map;

/**
 * This class represents a workflow for creating a new experiment. The bean name must match the appropriate bean column
 * value in the import_mapping_workflow db table
 */

@Prototype
@Slf4j
@Named("CreateNewExperimentWorkflow")
public class CreateNewExperimentWorkflow implements Workflow {

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
    }

    @Override
    public ImportPreviewResponse process(ImportContext context) throws Exception {

        ImportUpload upload = context.getUpload();
        boolean commit = context.isCommit();

        // Make sure the file does not contain obs unit ids before proceeding
        if (containsObsUnitIDs(context)) {
            // TODO: get file name
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Error detected in file, XXX.xls. ObsUnitIDs are detected. Import cannot proceed");
        }

        statusService.updateMessage(upload, "Checking existing experiment objects in brapi service and mapping data");

        ProcessedPhenotypeData phenotypeData = experimentPhenotypeService.extractPhenotypes(context);
        ProcessContext processContext = populateExistingPendingImportObjectsStep.process(context, phenotypeData);
        ProcessedData processedData = populateNewPendingImportObjectsStep.process(processContext, phenotypeData);
        ValidationErrors validationErrors = validatePendingImportObjectsStep.process(context);

        // short circuit if there were validation errors
        if (validationErrors.hasErrors()) {
            throw new ValidatorException(validationErrors);
        }

        ImportPreviewResponse response = buildImportPreviewResponse(processedData, upload);

        statusService.updateMappedData(upload, response, "Finished mapping data to brapi objects");

        // preview data
        if (!commit) {
            statusService.updateOk(upload);
            return response;
        }

        // commit data
        long totalObjects = getNewObjectCount(processedData);
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

    @Override
    public String getName() {
        return "CreateNewExperimentWorkflow";
    }

    // TODO: move to shared area
    private ImportPreviewResponse buildImportPreviewResponse(ProcessedData processedData,
                                                             ImportUpload upload) {
        Map<String, ImportPreviewStatistics> statistics = processedData.getStatistics();
        Map<Integer, PendingImport> mappedBrAPIImport = processedData.getMappedBrAPIImport();

        ImportPreviewResponse response = new ImportPreviewResponse();
        response.setStatistics(statistics);
        List<PendingImport> mappedBrAPIImportList = new ArrayList<>(mappedBrAPIImport.values());
        response.setRows(mappedBrAPIImportList);
        response.setDynamicColumnNames(upload.getDynamicColumnNamesList());
        return response;
    }

    // TODO: move to shared area
    private long getNewObjectCount(ProcessedData processedData) {
        // get total number of new brapi objects to create
        long totalObjects = 0;
        for (ImportPreviewStatistics stats : processedData.getStatistics().values()) {
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
}

