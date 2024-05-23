package org.breedinginsight.brapps.importer.services.processors.experiment.newenv.workflow;

import lombok.Getter;
import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflowResult;
import org.breedinginsight.brapps.importer.model.workflow.Workflow;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentWorkflow;

import javax.inject.Singleton;
import java.util.Optional;

@Getter
@Singleton
public class CreateNewEnvironmentWorkflow implements Workflow {
    private final ExperimentWorkflow.Workflow workflow;

    public CreateNewEnvironmentWorkflow(){
        this.workflow = ExperimentWorkflow.Workflow.APPEND_ENVIRONMENT;
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
