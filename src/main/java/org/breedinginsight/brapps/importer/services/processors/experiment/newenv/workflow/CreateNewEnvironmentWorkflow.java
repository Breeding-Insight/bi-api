package org.breedinginsight.brapps.importer.services.processors.experiment.newenv.workflow;

import lombok.Getter;
import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflowResult;
import org.breedinginsight.brapps.importer.model.workflow.ExperimentWorkflow;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentWorkflowNavigator;

import javax.inject.Singleton;
import java.util.Optional;

@Getter
@Singleton
public class CreateNewEnvironmentWorkflow implements ExperimentWorkflow {
    private final ExperimentWorkflowNavigator.Workflow workflow;

    public CreateNewEnvironmentWorkflow(){
        this.workflow = ExperimentWorkflowNavigator.Workflow.APPEND_ENVIRONMENT;
    }

    public Optional<ImportWorkflowResult> process(ImportServiceContext context) {
        // Workflow processing the context
        ImportWorkflow workflow = ImportWorkflow.builder()
                .id(getWorkflow().getId())
                .name(getWorkflow().getName())
                .build();

        // No-preview result
        Optional<ImportWorkflowResult> result = Optional.of(ImportWorkflowResult.builder()
                .workflow(workflow)
                .importPreviewResponse(Optional.empty())
                .build());

        // Skip this workflow unless appending a new environment
        if (context != null && !this.workflow.isEqual(context.getWorkflow())) {
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
        return 3;
    }

}
