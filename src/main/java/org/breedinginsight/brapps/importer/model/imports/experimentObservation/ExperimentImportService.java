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
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImportService;
import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.workflow.ImportContext;
import org.breedinginsight.brapps.importer.services.processors.ExperimentProcessor;
import org.breedinginsight.brapps.importer.services.processors.Processor;
import org.breedinginsight.brapps.importer.services.processors.ProcessorManager;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;

@Singleton
@Slf4j
public class ExperimentImportService implements BrAPIImportService {

    private final String IMPORT_TYPE_ID = "ExperimentImport";

    private final Provider<ExperimentProcessor> experimentProcessorProvider;
    private final Provider<ProcessorManager> processorManagerProvider;

    @Inject
    public ExperimentImportService(Provider<ExperimentProcessor> experimentProcessorProvider, Provider<ProcessorManager> processorManagerProvider)
    {
        this.experimentProcessorProvider = experimentProcessorProvider;
        this.processorManagerProvider = processorManagerProvider;
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
    public ImportPreviewResponse process(ImportServiceContext context)
            throws Exception {

        ImportPreviewResponse response = null;
        List<Processor> processors = List.of(experimentProcessorProvider.get());

        if (context.getWorkflow() != null) {
            log.info("Workflow: " + context.getWorkflow().getName());

            // TODO: change when workflows selection is ready
            if (context.getWorkflow().getName().equals("CreateNewExperimentWorkflow")) {
                ImportContext importContext = ImportContext.builder()
                        .importRows(context.getBrAPIImports())
                        .user(context.getUser())
                        .data(context.getData())
                        .commit(context.isCommit())
                        .upload(context.getUpload())
                        .program(context.getProgram())
                        .build();

               return context.getWorkflow().process(importContext);
            }
        }

        // TODO: change to calling workflow process instead of processor manager
        // other workflows besides create will pass through to old flow
        response = processorManagerProvider.get().process(context.getBrAPIImports(),
                processors,
                context.getData(),
                context.getProgram(),
                context.getUpload(),
                context.getUser(),
                context.isCommit());
        return response;

    }
}

