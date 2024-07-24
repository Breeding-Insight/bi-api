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

package org.breedinginsight.brapps.importer.model.workflow;

import io.micronaut.core.order.Ordered;
import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/**
 * This Functional Interface represents a Workflow that can be executed as part of an import process.
 * It extends the Ordered interface to allow workflows to be ordered in a sequence.
 */
@FunctionalInterface
public interface Workflow extends Ordered {

    /**
     * Process method that defines the logic to be executed as part of the workflow.
     *
     * @param context the ImportServiceContext object containing necessary information for the workflow
     * @return an Optional of ImportWorkflowResult representing the result of the workflow execution
     */
    Optional<ImportWorkflowResult> process(ImportServiceContext context);

    /**
     * Default method to get a list of workflows.
     * This method provides a default implementation returning an empty list.
     *
     * @return a List of ImportWorkflow containing workflows
     */
    default List<ImportWorkflow> getWorkflows() {
        // Default implementation for getWorkflows method
        return new ArrayList<>();
    }
}
