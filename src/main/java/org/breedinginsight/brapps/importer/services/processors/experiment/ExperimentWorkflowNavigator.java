package org.breedinginsight.brapps.importer.services.processors.experiment;

import io.micronaut.context.annotation.Primary;
import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.ExperimentWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflowResult;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Primary
@Singleton
public class ExperimentWorkflowNavigator implements ExperimentWorkflow {
    private final List<ExperimentWorkflow> workflows;

    @Inject
    public ExperimentWorkflowNavigator(List<ExperimentWorkflow> workflows) {
        this.workflows = workflows;
    }

    /**
     * Process the import service context by executing a series of workflows in order
     *
     * This method iterates over the list of workflows provided, executing each workflow's process method
     * with the given import service context. It then filters out empty results and returns the first non-empty result.
     *
     * @param context The import service context containing the data to be processed
     * @return An Optional containing the first non-empty ImportWorkflowResult from the executed workflows, or an empty Optional if no non-empty result is found
     */
    @Override
    public Optional<ImportWorkflowResult> process(ImportServiceContext context) {
        /**
         * Have each workflow in order process the context, returning the first non-empty result
         */
        return workflows.stream()
                .map(workflow->workflow.process(context))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /**
     * Retrieves a list of ImportWorkflow objects containing metadata about each workflow that processed the import context.
     *
     * @return List of ImportWorkflow objects with workflow metadata
     */
    public List<ImportWorkflow> getWorkflows() {
        List<ImportWorkflow> workflowSummaryList = workflows.stream()
                .map(workflow -> workflow.process(null)) // Process each workflow with a null context
                .filter(Optional::isPresent) // Filter out any workflows that do not return a result
                .map(Optional::get) // Extract the result from Optional
                .map(result -> result.getWorkflow()) // Retrieve the workflow metadata
                .collect(Collectors.toList()); // Collect the workflow metadata into a list

        // Set the order field for each workflow based on its position in the list
        for (int i = 0; i < workflowSummaryList.size(); i++) {
            workflowSummaryList.get(i).setOrder(i); // Set the order for each workflow
        }

        return workflowSummaryList; // Return the list of workflow metadata
    }
}
