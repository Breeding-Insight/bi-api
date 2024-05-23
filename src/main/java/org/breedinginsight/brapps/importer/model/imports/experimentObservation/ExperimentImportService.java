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

package org.breedinginsight.brapps.importer.model.imports.experimentObservation;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImportService;
import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflow;
import org.breedinginsight.brapps.importer.services.processors.ExperimentProcessor;
import org.breedinginsight.brapps.importer.services.processors.ProcessorManager;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentWorkflowNavigator;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.List;

@Singleton
@Slf4j
public class ExperimentImportService implements BrAPIImportService {

    private final String IMPORT_TYPE_ID = "ExperimentImport";

    private final Provider<ExperimentProcessor> experimentProcessorProvider;
    private final Provider<ProcessorManager> processorManagerProvider;
    private final ExperimentWorkflowNavigator workflowNavigator;

    @Inject
    public ExperimentImportService(Provider<ExperimentProcessor> experimentProcessorProvider,
                                   Provider<ProcessorManager> processorManagerProvider,
                                   ExperimentWorkflowNavigator workflowNavigator)
    {
        this.experimentProcessorProvider = experimentProcessorProvider;
        this.processorManagerProvider = processorManagerProvider;
        this.workflowNavigator = workflowNavigator;
    }

    @Override
    public ExperimentObservation getImportClass() {
        return new ExperimentObservation();
    }

    @Override
    public String getImportTypeId() {
        return IMPORT_TYPE_ID;
    }

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

