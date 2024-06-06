package org.breedinginsight.brapps.importer.services.processors.experiment.append.workflow;

import lombok.Getter;
import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflowResult;
import org.breedinginsight.brapps.importer.model.workflow.ExperimentWorkflow;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentWorkflowNavigator;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.GetExistingBrAPIData;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.Transaction;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ValidateAllRowsHaveIDs;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Getter
@Singleton
public class AppendOverwritePhenotypesWorkflow implements ExperimentWorkflow {
    private final ExperimentWorkflowNavigator.Workflow workflow;
    private final ExpUnitMiddleware middleware;

    @Inject
    public AppendOverwritePhenotypesWorkflow(Transaction transaction,
                                             ValidateAllRowsHaveIDs validateAllRowsHaveIDs,
                                             GetExistingBrAPIData getExistingBrAPIData){
        this.workflow = ExperimentWorkflowNavigator.Workflow.APPEND_OVERWRITE;
        this.middleware = (ExpUnitMiddleware) ExpUnitMiddleware.link(transaction, validateAllRowsHaveIDs, getExistingBrAPIData);
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

        // Start processing the import...
        ImportContext importContext = ImportContext.builder()
                .upload(context.getUpload())
                .importRows(context.getBrAPIImports())
                .data(context.getData())
                .program(context.getProgram())
                .user(context.getUser())
                .commit(context.isCommit())
                .build();
        ExpUnitMiddlewareContext workflowContext = ExpUnitMiddlewareContext.builder().importContext(importContext).build();
        this.middleware.process(workflowContext);
        return result;
    }

    @Override
    public int getOrder() {
        return 2;
    }

}
