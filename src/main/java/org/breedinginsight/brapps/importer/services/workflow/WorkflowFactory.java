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

package org.breedinginsight.brapps.importer.services.workflow;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.daos.ImportMappingWorkflowDAO;
import org.breedinginsight.brapps.importer.model.workflow.ImportMappingWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.Workflow;

import javax.inject.Inject;
import java.util.Optional;
import java.util.UUID;

@Factory
@Slf4j
public class WorkflowFactory {

    private final ImportMappingWorkflowDAO importMappingWorkflowDAO;
    private final ApplicationContext applicationContext;

    @Inject
    public WorkflowFactory(ImportMappingWorkflowDAO importMappingWorkflowDAO,
                           ApplicationContext applicationContext) {
        this.importMappingWorkflowDAO = importMappingWorkflowDAO;
        this.applicationContext = applicationContext;
    }

    /**
     * Produces the appropriate workflow instance based on the import context
     *
     * @param context the import context
     * @return an Optional containing the workflow if id is not null, otherwise an empty Optional
     *
     * @throws IllegalStateException
     * @throws NoSuchBeanException
     */
    public Optional<Workflow> getWorkflow(UUID workflowId) {

        if (workflowId != null) {
            // construct workflow from db record
            Optional<ImportMappingWorkflow> workflowOptional = importMappingWorkflowDAO.getWorkflowById(workflowId);

            ImportMappingWorkflow importMappingworkflow = workflowOptional.orElseThrow(() -> {
                String msg = "Must have record in db for workflowId";
                log.error(msg);
                return new IllegalStateException(msg);
            });

            // newer versions of micronaut have fancier ways to do this using annotations with provider but as
            // far as I can tell it's not available in 2.5
            Workflow workflow;
            try {
                workflow = applicationContext.getBean(Workflow.class, Qualifiers.byName(importMappingworkflow.getBean()));
            } catch (NoSuchBeanException e) {
                log.error("Could not find workflow class implementation for bean: " + importMappingworkflow.getBean());
                throw e;
            }

            return Optional.of(workflow);
        }

        return Optional.empty();
    }
}
