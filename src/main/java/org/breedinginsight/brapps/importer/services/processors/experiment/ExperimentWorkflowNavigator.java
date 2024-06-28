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
    public List<ImportWorkflow> getWorkflows() {
        /** Each workflow returns in the field workflow the metadata about the workflow that processed the import context.
         *  Loop over all workflows, processing a null context, to collect just the metadata for each workflow
         */
        List<ImportWorkflow> workflowSummaryList = workflows.stream()
                .map(workflow->workflow.process(null))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(result->result.getWorkflow())
                .collect(Collectors.toList());

        // The order field for each workflow is set to the order in the navigator list
        for (int i = 0; i < workflowSummaryList.size(); i++) {
            workflowSummaryList.get(i).setOrder(i);
        }

        return workflowSummaryList;
    }

    /**
     * Possible choices of metadata that an experiment workflow instance can use to identify itself
     */
    public enum Workflow {
        NEW_OBSERVATION("new-experiment","Create new experiment"),
        APPEND_OVERWRITE("append-dataset", "Append experimental dataset");

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
