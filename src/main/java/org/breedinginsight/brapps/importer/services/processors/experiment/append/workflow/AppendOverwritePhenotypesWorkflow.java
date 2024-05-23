package org.breedinginsight.brapps.importer.services.processors.experiment.append.workflow;

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
public class AppendOverwritePhenotypesWorkflow implements Workflow {
    private final ExperimentWorkflow.Workflow workflow;

    public AppendOverwritePhenotypesWorkflow(){
        this.workflow = ExperimentWorkflow.Workflow.APPEND_OVERWRITE;
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
        return result;
    }

    @Override
    public int getOrder() {
        return 2;
    }

}
