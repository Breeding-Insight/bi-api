package org.breedinginsight.brapps.importer.services.processors.experiment.append.workflow;

import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;
import org.breedinginsight.brapps.importer.model.workflow.Action;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflowResult;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentImportWorkflow;

import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class AppendOverwriteObservationWorkflow implements ImportWorkflow {
    private final ExperimentImportWorkflow.ImportAction action;

    public AppendOverwriteObservationWorkflow(){
        this.action = ExperimentImportWorkflow.ImportAction.APPEND_OVERWRITE;
    }

    @Override
    public Optional<ImportWorkflowResult> process(ImportServiceContext context) {
        // Workflow processing the context
        Action workflow = new Action(action.getUrlFragment(), this.getOrder());

        // No-preview result
        Optional<ImportWorkflowResult> result = Optional.of(ImportWorkflowResult.builder()
                .action(workflow)
                .importPreviewResponse(Optional.empty())
                .build());

        // Skip this workflow unless appending or overwriting observation data
        if (context != null && !action.isEqual(context.getAction())) {
            return Optional.empty();
        }

        // Skip processing if no context, but return no-preview result for this workflow
        if (context == null) {
            return result;
        }

        // Start processing the import...
        return result;
    }

    @Override
    public int getOrder() {
        return 2;
    }

    public ExperimentImportWorkflow.ImportAction getAction() {
        return this.action;
    }
}
