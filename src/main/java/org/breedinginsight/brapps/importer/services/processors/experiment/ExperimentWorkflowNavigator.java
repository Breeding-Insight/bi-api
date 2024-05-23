package org.breedinginsight.brapps.importer.services.processors.experiment;

import io.micronaut.context.annotation.Primary;
import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.ExperimentWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflowResult;

import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Primary
@Singleton
public class ExperimentWorkflowNavigator implements ExperimentWorkflow {
    private final List<ExperimentWorkflow> workflows;

    public ExperimentWorkflowNavigator(List<ExperimentWorkflow> workflows) {
        this.workflows = workflows;
    }

    @Override
    public Optional<ImportWorkflowResult> process(ImportServiceContext context) {
        return workflows.stream()
                .map(workflow->workflow.process(context))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }
    public List<ImportWorkflow> getWorkflows() {
        return workflows.stream()
                .map(workflow->workflow.process(null))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(result->result.getWorkflow())
                .collect(Collectors.toList());
    }

    public enum Workflow {
        NEW_OBSERVATION("new-experiment","Create new experiment"),
        APPEND_OVERWRITE("append-dataset", "Append experimental dataset"),
        APPEND_ENVIRONMENT("append-environment", "Create new experimental environment");

        private String urlFragment;
        private String displayName;

        Workflow(String urlFragment, String displayName) {

            this.urlFragment = urlFragment;
            this.displayName = displayName;
        }

        public String getUrlFragment() {
            return urlFragment;
        }
        public String getDisplayName() { return displayName; }

        public boolean isEqual(String value) {
            return Optional.ofNullable(urlFragment.equals(value)).orElse(false);
        }
    }
}
