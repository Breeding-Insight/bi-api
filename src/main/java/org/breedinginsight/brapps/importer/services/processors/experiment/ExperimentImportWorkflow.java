package org.breedinginsight.brapps.importer.services.processors.experiment;

import io.micronaut.context.annotation.Primary;
import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;
import org.breedinginsight.brapps.importer.model.workflow.Action;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflowResult;

import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Primary
@Singleton
public class ExperimentImportWorkflow implements ImportWorkflow {
    private final List<ImportWorkflow> workflows;

    public ExperimentImportWorkflow(List<ImportWorkflow> workflows) {
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
    public List<Action> getWorkflows() {
        return workflows.stream()
                .map(workflow->workflow.process(null))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(result->result.getAction())
                .collect(Collectors.toList());
    }

    public enum ImportAction {
        APPEND_OVERWRITE("append-overwrite-observation"),
        NEW_OBSERVATION("new-observation"),
        APPEND_ENVIRONMENT("append-environment");

        private String urlFragment;

        ImportAction(String urlFragment) {
            this.urlFragment = urlFragment;
        }

        public String getUrlFragment() {
            return urlFragment;
        }

        public boolean isEqual(String value) {
            return Optional.ofNullable(urlFragment.equals(value)).orElse(false);
        }
    }
}
