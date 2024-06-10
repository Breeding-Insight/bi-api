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
        // Each workflow returns in the field workflow the metadata about the workflow that processed the import context.
        // Loop over all workflows, processing a null context, to collect just the metadata
        List<ImportWorkflow> workflowSummaryList = workflows.stream()
                .map(workflow->workflow.process(null))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(result->result.getWorkflow())
                .collect(Collectors.toList());

        // The order field for each workflow is set to the order in the list
        for (int i = 0; i < workflowSummaryList.size(); i++) {
            workflowSummaryList.get(i).setOrder(i);
        }

        return workflowSummaryList;
    }

    public enum Workflow {
        NEW_OBSERVATION("new-experiment","Create new experiment"),
        APPEND_OVERWRITE("append-dataset", "Append experimental dataset"),
        APPEND_ENVIRONMENT("append-environment", "Create new experimental environment");

        private String id;
        private String name;

        Workflow(String id, String name) {

            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }
        public String getName() { return name; }

        public boolean isEqual(String value) {
            return Optional.ofNullable(id.equals(value)).orElse(false);
        }
    }
}
