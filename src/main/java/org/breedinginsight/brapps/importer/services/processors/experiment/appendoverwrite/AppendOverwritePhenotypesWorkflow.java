package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite;

import lombok.Getter;
import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflowResult;
import org.breedinginsight.brapps.importer.model.workflow.ExperimentWorkflow;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentWorkflowNavigator;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.Transaction;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitIDValidation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit.BrAPICommit;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.initialize.WorkflowInitialization;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.ImportTableProcess;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Optional;

@Getter
@Singleton
public class AppendOverwritePhenotypesWorkflow implements ExperimentWorkflow {
    private final ExperimentWorkflowNavigator.Workflow workflow;
    private final ExpUnitMiddleware middleware;

    @Inject
    public AppendOverwritePhenotypesWorkflow(Transaction transaction,
                                             ExpUnitIDValidation expUnitIDValidation,
                                             WorkflowInitialization workflowInitialization,
                                             ImportTableProcess importTableProcess,
                                             BrAPICommit brAPICommit){
        this.workflow = ExperimentWorkflowNavigator.Workflow.APPEND_OVERWRITE;
        this.middleware = (ExpUnitMiddleware) ExpUnitMiddleware.link(
                transaction,
                expUnitIDValidation,
                workflowInitialization,
                importTableProcess,
                brAPICommit);
    }

    @Override
    public Optional<ImportWorkflowResult> process(ImportServiceContext context) {
        // Workflow processing the context
        ImportWorkflow workflow = ImportWorkflow.builder()
                .urlFragment(getWorkflow().getUrlFragment())
                .displayName(getWorkflow().getDisplayName())
                .build();

        // No-preview result
        Optional<ImportWorkflowResult> result = Optional.of(ImportWorkflowResult.builder()
                .workflow(workflow)
                .importPreviewResponse(Optional.empty())
                .build());

        // Skip this workflow unless appending or overwriting observation data
        if (context != null && !this.workflow.isEqual(context.getWorkflow())) {
            return Optional.empty();
        }

        // Skip processing if no context, but return no-preview result for this workflow
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
        ExpUnitMiddlewareContext workflowContext = ExpUnitMiddlewareContext.builder().importContext(importContext).build();

        // Process the workflow
        ExpUnitMiddlewareContext processedContext = this.middleware.process(workflowContext);

        // TODO: Rethrow any exceptions caught during processing the context
//        Optional.ofNullable(processedContext.getExpUnitContext().getMiddlewareError()).ifPresent(e -> {
//            Constructor<? extends Exception> constructor = e.getError().getClass().getConstructor(String.class, Throwable.class);
//            Exception newException = constructor.newInstance(e.getError().getMessage(), e);
//            throw newException;
//        });

        // Shape and return the workflow response
        ImportPreviewResponse response = new ImportPreviewResponse();
        response.setStatistics(processedContext.getExpUnitContext().getStatistic().constructPreviewMap());
        response.setRows(new ArrayList<>(processedContext.getImportContext().getMappedBrAPIImport().values()));
        response.setDynamicColumnNames(processedContext.getImportContext().getUpload().getDynamicColumnNamesList());

        result.ifPresent(importWorkflowResult -> importWorkflowResult.setImportPreviewResponse(Optional.of(response)));

        return result;
    }

    @Override
    public int getOrder() {
        return 2;
    }

}
