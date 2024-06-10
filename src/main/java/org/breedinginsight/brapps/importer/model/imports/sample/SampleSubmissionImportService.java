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

package org.breedinginsight.brapps.importer.model.imports.sample;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImportService;
import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflow;
import org.breedinginsight.brapps.importer.services.processors.Processor;
import org.breedinginsight.brapps.importer.services.processors.ProcessorManager;
import org.breedinginsight.brapps.importer.services.processors.SampleSubmissionProcessor;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
@Slf4j
public class SampleSubmissionImportService implements BrAPIImportService {
    private final String IMPORT_TYPE_ID = "SampleImport";
    private final Provider<SampleSubmissionProcessor> sampleProcessorProvider;
    private final Provider<ProcessorManager> processorManagerProvider;

    @Inject
    public SampleSubmissionImportService(Provider<SampleSubmissionProcessor> sampleProcessorProvider, Provider<ProcessorManager> processorManagerProvider)
    {
        this.sampleProcessorProvider = sampleProcessorProvider;
        this.processorManagerProvider = processorManagerProvider;
    }

    @Override
    public String getImportTypeId() {
        return IMPORT_TYPE_ID;
    }

    @Override
    public BrAPIImport getImportClass() {
        return new SampleSubmissionImport();
    }

    @Override
    public List<ImportWorkflow> getWorkflows() {
        return new ArrayList<>();
    }

    @Override
    public ImportPreviewResponse process(ImportServiceContext context) throws Exception {
        List<Processor> processors = List.of(sampleProcessorProvider.get());
        return processorManagerProvider.get().process(context.getBrAPIImports(),
                processors,
                context.getData(),
                context.getProgram(),
                context.getUpload(),
                context.getUser(),
                context.isCommit());
    }
}
