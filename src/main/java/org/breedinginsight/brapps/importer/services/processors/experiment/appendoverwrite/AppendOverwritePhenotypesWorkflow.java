package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflowResult;
import org.breedinginsight.brapps.importer.model.workflow.ExperimentWorkflow;
import org.breedinginsight.brapps.importer.services.ImportStatusService;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentWorkflowNavigator;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.Transaction;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitIDValidation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit.BrAPICommit;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.initialize.WorkflowInitialization;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.ImportTableProcess;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
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
    private final ExpUnitMiddleware importPreviewMiddleware;
    private final ExpUnitMiddleware brapiCommitMiddleware;
    private final ImportStatusService statusService;

    @Inject
    public AppendOverwritePhenotypesWorkflow(Transaction transaction,
                                             ExpUnitIDValidation expUnitIDValidation,
                                             WorkflowInitialization workflowInitialization,
                                             ImportTableProcess importTableProcess,
                                             BrAPICommit brAPICommit,
                                             ImportStatusService statusService){
        this.statusService = statusService;
        this.workflow = ExperimentWorkflowNavigator.Workflow.APPEND_OVERWRITE;
        this.importPreviewMiddleware = (ExpUnitMiddleware) ExpUnitMiddleware.link(
                transaction,
                expUnitIDValidation,
                workflowInitialization,
                importTableProcess);
        this.brapiCommitMiddleware = (ExpUnitMiddleware) ExpUnitMiddleware.link(brAPICommit);
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
                .build());

        // Skip this workflow unless appending or overwriting observation data
        if (context != null && !this.workflow.isEqual(context.getWorkflow())) {
            return Optional.empty();
        }

        // Skip processing if no context, but return no-preview result with metadata for this workflow
        if (context == null) {
            return result;
        }

        // Build the context for processing the import
        ImportContext importContext = ImportContext.builder()
                .upload(context.getUpload())
                .importRows(context.getBrAPIImports())
                .data(context.getData())
                .program(context.getProgram())
                .user(context.getUser())
                .commit(context.isCommit())
                .build();
        ExpUnitMiddlewareContext workflowContext = ExpUnitMiddlewareContext.builder()
                .importContext(importContext)
                .expUnitContext(new ExpUnitContext())
                .build();

        // Process the import preview
        ExpUnitMiddlewareContext processedPreviewContext = this.importPreviewMiddleware.process(workflowContext);

        // TODO: Rethrow any exceptions caught during processing the context
//        Optional.ofNullable(processedContext.getExpUnitContext().getMiddlewareError()).ifPresent(e -> {
//            Constructor<? extends Exception> constructor = e.getError().getClass().getConstructor(String.class, Throwable.class);
//            Exception newException = constructor.newInstance(e.getError().getMessage(), e);
//            throw newException;
//        });

        // BUild and return the preview response
        ImportPreviewResponse response = new ImportPreviewResponse();
        response.setStatistics(processedPreviewContext.getExpUnitContext().getStatistic().constructPreviewMap());
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
            ExpUnitMiddlewareContext brapiCommittedContext = this.brapiCommitMiddleware.process(processedPreviewContext);

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
