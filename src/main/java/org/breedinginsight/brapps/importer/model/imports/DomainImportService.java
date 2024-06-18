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

package org.breedinginsight.brapps.importer.model.imports;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflowResult;
import org.breedinginsight.brapps.importer.model.workflow.Workflow;
import org.breedinginsight.brapps.importer.services.processors.ExperimentProcessor;
import org.breedinginsight.brapps.importer.services.processors.ProcessorManager;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentWorkflowNavigator;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Singleton
@Slf4j
public abstract class DomainImportService implements BrAPIImportService {

    // TODO: delete processor fields once WorkflowNavigator is used
    private final Provider<ExperimentProcessor> experimentProcessorProvider;
    private final Provider<ProcessorManager> processorManagerProvider;
    private final Workflow workflowNavigator;


    public DomainImportService(Provider<ExperimentProcessor> experimentProcessorProvider,
                               Provider<ProcessorManager> processorManagerProvider,
                               Workflow workflowNavigator)
    {
        this.experimentProcessorProvider = experimentProcessorProvider;
        this.processorManagerProvider = processorManagerProvider;
        this.workflowNavigator = workflowNavigator;
    }

    @Override
    public String getMissingColumnMsg(String columnName) {
        return "Column heading does not match template or ontology";
    }
    @Override
    public List<ImportWorkflow> getWorkflows() throws Exception{
        return workflowNavigator.getWorkflows();
    }

    @Override
    public ImportPreviewResponse process(ImportServiceContext context)
            throws Exception {

        Optional.ofNullable(context.getWorkflow())
                .filter(workflow -> !workflow.isEmpty())
                .ifPresent(workflow -> log.info("Workflow: " + workflow));

        // TODO: return results from WorkflowNavigator once processing logic is in separate workflows
        // return workflowNavigator.process(context).flatMap(ImportWorkflowResult::getImportPreviewResponse).orElse(null);
        if ("new-experiment".equals(context.getWorkflow())) {
            return workflowNavigator.process(context).flatMap(ImportWorkflowResult::getImportPreviewResponse).orElse(null);
        } else {
            return processorManagerProvider.get().process(context.getBrAPIImports(),
                    List.of(experimentProcessorProvider.get()),
                    context.getData(),
                    context.getProgram(),
                    context.getUpload(),
                    context.getUser(),
                    context.isCommit());
        }
    }
}

