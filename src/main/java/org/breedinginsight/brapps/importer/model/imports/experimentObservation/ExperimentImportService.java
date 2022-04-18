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
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImportService;
import org.breedinginsight.brapps.importer.model.imports.germplasm.GermplasmImport;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.services.processors.*;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.services.exceptions.ValidatorException;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.List;

@Singleton
@Slf4j
public class ExperimentImportService extends BrAPIImportService {

    private final String IMPORT_TYPE_ID = "ExperimentImport";

    private Provider<ExperimentProcessor> experimentProcessorProvider;
    private Provider<ProcessorManager> processorManagerProvider;

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
    public ImportPreviewResponse process(List<BrAPIImport> brAPIImports, Table data, Program program, ImportUpload upload, User user, Boolean commit)
            throws UnprocessableEntityException, ValidatorException, ApiException {

        ImportPreviewResponse response = null;
        List<Processor> processors = List.of(experimentProcessorProvider.get());
        try {
            response = processorManagerProvider.get().process(brAPIImports, processors, program, upload, user, commit);
        } catch (ValidatorException | ApiException e) {
            log.error(e.getMessage());
        }
        return response;

        response = processorManagerProvider.get().process(brAPIImports, processors, program, upload, user, commit);

        return response;
    }
}

