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

package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.workflow.ExperimentWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflowResult;
import org.breedinginsight.brapps.importer.services.ImportStatusService;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentWorkflowNavigator;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.AppendOverwriteIDValidation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit.BrAPICommit;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.initialize.WorkflowInitialization;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.ImportTableProcess;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteWorkflowContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.MiddlewareException;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Optional;
@Slf4j
@Getter
@Singleton
public class AppendOverwritePhenotypesWorkflow implements ExperimentWorkflow {
    private final ExperimentWorkflowNavigator.Workflow workflow;
    private final AppendOverwriteMiddleware importPreviewMiddleware;
    private final AppendOverwriteMiddleware brapiCommitMiddleware;
    private final ImportStatusService statusService;

    @Inject
    public AppendOverwritePhenotypesWorkflow(AppendOverwriteIDValidation expUnitIDValidation,
                                             WorkflowInitialization workflowInitialization,
                                             ImportTableProcess importTableProcess,
                                             BrAPICommit brAPICommit,
                                             ImportStatusService statusService){
        this.statusService = statusService;
        this.workflow = ExperimentWorkflowNavigator.Workflow.APPEND_OVERWRITE;
        this.importPreviewMiddleware = (AppendOverwriteMiddleware) AppendOverwriteMiddleware.link(
                expUnitIDValidation,
                workflowInitialization,
                importTableProcess);
        this.brapiCommitMiddleware = (AppendOverwriteMiddleware) AppendOverwriteMiddleware.link(brAPICommit);
    }

    @Override
    public Optional<ImportWorkflowResult> process(ImportServiceContext context) {
        // Metadata about this workflow processing the context
        ImportWorkflow workflow = ImportWorkflow.builder()
                .id(getWorkflow().getId())
                .name(getWorkflow().getName())
                .build();

        // No-preview result
        Optional<ImportWorkflowResult> result = Optional.of(ImportWorkflowResult.builder()
                .workflow(workflow)  // attach metadata of this workflow to response
                .importPreviewResponse(Optional.empty())
                .caughtException(Optional.empty())
                .build());

        // Skip this workflow unless appending or overwriting observation data
        if (context != null && !this.workflow.isEqual(context.getWorkflow())) {
            return Optional.empty();
        }

        // Skip processing if no context, but return no-preview result with metadata for this workflow
        if (context == null) {
            return result;
        }

        // Build the workflow context for processing the import
        ImportContext importContext = ImportContext.builder()
                .upload(context.getUpload())
                .importRows(context.getBrAPIImports())
                .data(context.getData())
                .program(context.getProgram())
                .user(context.getUser())
                .commit(context.isCommit())
                .build();
        AppendOverwriteMiddlewareContext workflowContext = AppendOverwriteMiddlewareContext.builder()
                .importContext(importContext)
                .appendOverwriteWorkflowContext(new AppendOverwriteWorkflowContext())
                .build();

        // Process the import preview
        AppendOverwriteMiddlewareContext processedPreviewContext = this.importPreviewMiddleware.process(workflowContext);

        // Stop and return any errors that occurred while processing
        Optional<MiddlewareException> previewException = Optional.ofNullable(processedPreviewContext.getAppendOverwriteWorkflowContext().getProcessError());
        if (previewException.isPresent() ) {
            log.debug(String.format("%s in %s", previewException.get().getException().getClass().getName(), previewException.get().getLocalTransactionName()));
            result.ifPresent(importWorkflowResult -> importWorkflowResult.setCaughtException(Optional.ofNullable(previewException.get().getException())));
            return result;
        }

        // BUild and return the preview response
        ImportPreviewResponse response = new ImportPreviewResponse();
        response.setStatistics(processedPreviewContext.getAppendOverwriteWorkflowContext().getStatistic().constructPreviewMap());
        response.setRows(new ArrayList<>(processedPreviewContext.getImportContext().getMappedBrAPIImport().values()));
        response.setDynamicColumnNames(processedPreviewContext.getImportContext().getUpload().getDynamicColumnNamesList());

        result.ifPresent(importWorkflowResult -> importWorkflowResult.setImportPreviewResponse(Optional.of(response)));

        log.debug("Finished mapping data to brapi objects");
        statusService.updateMappedData(context.getUpload(), response, "Finished mapping data to brapi objects");

        if (!context.isCommit()) {
            statusService.updateOk(context.getUpload());
            return result;
        } else {

            // get total number of new brapi objects to create
            long totalObjects = response.getStatistics().values().stream()
                    .mapToLong(ImportPreviewStatistics::getNewObjectCount)  // Extract newObjectCount from each ImportStatistics entry
                    .sum();  // Sum the newObjectCount values
            log.debug("Starting upload to brapi service");
            statusService.startUpload(context.getUpload(), totalObjects, "Starting upload to brapi service");
            log.debug("Creating new objects in brapi service");
            statusService.updateMessage(context.getUpload(), "Creating new objects in brapi service");

            // Commit the changes from the processed import preview to the BrAPI service
            AppendOverwriteMiddlewareContext brapiCommittedContext = this.brapiCommitMiddleware.process(processedPreviewContext);

            Optional<MiddlewareException> brapiCommitException = Optional.ofNullable(brapiCommittedContext.getAppendOverwriteWorkflowContext().getProcessError());
            if (brapiCommitException.isPresent() ) {
                log.debug(String.format("%s in %s", brapiCommitException.get().getException().getClass()), brapiCommitException.get().getLocalTransactionName());
                result.ifPresent(importWorkflowResult -> importWorkflowResult.setCaughtException(Optional.ofNullable(brapiCommitException.get().getException())));
                return result;
            }

            log.debug("Completed upload to brapi service");
            statusService.finishUpload(context.getUpload(), totalObjects, "Completed upload to brapi service");
        }

        return result;
    }

    @Override
    public int getOrder() {
        return 2;
    }

}
