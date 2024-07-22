/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    /** Micronaut scans and collects in a List all instances of ExperimentWorkflow not annotated as @Primary and
     * automatically makes the list available to inject into the constructor, which is set here to the workflows field.
     * The order in the list is determined by the sort value returned from each instance by calling getOrder().
     * Instances returning a lower sort value will appear in the list before instances returning higher sort values.
     */
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
                .map(ImportWorkflowResult::getWorkflow) // Retrieve the workflow metadata
                .collect(Collectors.toList()); // Collect the workflow metadata into a list

        // Set the order field for each workflow based on its position in the list
        for (int i = 0; i < workflowSummaryList.size(); i++) {
            workflowSummaryList.get(i).setOrder(i); // Set the order for each workflow
        }

        return workflowSummaryList; // Return the list of workflow metadata
    }

    /**
     * The Workflow enum represents different workflow types that can be associated with an experiment.
     */
    public enum Workflow {

        /**
         * Represents a new observation workflow where a new experiment is created.
         * ID: "new-experiment"
         * Name: "Create new experiment"
         */
        NEW_OBSERVATION("new-experiment","Create new experiment"),

        /**
         * Represents an append or overwrite workflow where experimental dataset is appended.
         * ID: "append-dataset"
         * Name: "Append experimental dataset"
         */
        APPEND_OVERWRITE("append-dataset", "Append experimental dataset");

        private String id;
        private String name;

        /**
         * Constructor for the Workflow enum to initialize ID and Name.
         * @param id The ID of the workflow.
         * @param name The name of the workflow.
         */
        Workflow(String id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * Get the ID of the workflow.
         * @return The ID of the workflow.
         */
        public String getId() {
            return id;
        }

        /**
         * Get the name of the workflow.
         * @return The name of the workflow.
         */
        public String getName() {
            return name;
        }

        /**
         * Check if the given value is equal to the ID of the workflow.
         * @param value The value to compare with the workflow ID.
         * @return true if the value is equal to the ID, false otherwise.
         */
        public boolean isEqual(String value) {
            return value.equals(id);
        }
    }
}
