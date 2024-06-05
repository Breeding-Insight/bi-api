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
import org.breedinginsight.brapps.importer.model.workflow.Workflow;
import org.breedinginsight.brapps.importer.services.processors.ExperimentProcessor;
import org.breedinginsight.brapps.importer.services.processors.ProcessorManager;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentWorkflowNavigator;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.List;

@Singleton
@Slf4j
public abstract class DomainImportService implements BrAPIImportService {

    private final Provider<ExperimentProcessor> experimentProcessorProvider;
    private final Provider<ProcessorManager> processorManagerProvider;
    private final Workflow workflowNavigator;

    @Inject
    public DomainImportService(Provider<ExperimentProcessor> experimentProcessorProvider,
                               Provider<ProcessorManager> processorManagerProvider)
    {
        this.experimentProcessorProvider = experimentProcessorProvider;
        this.processorManagerProvider = processorManagerProvider;
        this.workflowNavigator = getNavigator();
    }

    protected abstract Workflow getNavigator();
    @Override
    public String getMissingColumnMsg(String columnName) {
        return "Column heading does not match template or ontology";
    }
    @Override
    public List<ImportWorkflow> getWorkflows() {
        return workflowNavigator.getWorkflows();
    }

    @Override
    public ImportPreviewResponse process(ImportServiceContext context)
            throws Exception {

        if (!context.getWorkflow().isEmpty()) {
            log.info("Workflow: " + context.getWorkflow());
        }

        return workflowNavigator.process(context).flatMap(r->r.getImportPreviewResponse()).orElse(null);
    }
}

