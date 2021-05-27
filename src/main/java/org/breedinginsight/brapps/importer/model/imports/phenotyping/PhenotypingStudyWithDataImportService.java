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
package org.breedinginsight.brapps.importer.model.imports.phenotyping;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImportService;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.services.processors.*;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.services.exceptions.ValidatorException;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import java.util.List;

@Prototype
@Slf4j
public class PhenotypingStudyWithDataImportService extends BrAPIImportService {

    private final String IMPORT_TYPE_ID = "PhenotypingStudyWithDataImport";

    private GermplasmProcessor germplasmProcessor;
    private TrialProcessor trialProcessor;
    private StudyProcessor studyProcessor;
    private ObservationUnitProcessor observationUnitProcessor;
    private ProcessorManager processorManager;

    @Inject
    public PhenotypingStudyWithDataImportService(GermplasmProcessor germplasmProcessor,
                                                 TrialProcessor trialProcessor,
                                                 StudyProcessor studyProcessor,
                                                 ObservationUnitProcessor observationUnitProcessor,
                                                 ProcessorManager processorManager)
    {
        this.germplasmProcessor = germplasmProcessor;
        this.trialProcessor = trialProcessor;
        this.studyProcessor = studyProcessor;
        this.observationUnitProcessor = observationUnitProcessor;
        this.processorManager = processorManager;
    }

    @Override
    public PhenotypingStudyWithDataImport getImportClass() {
        return new PhenotypingStudyWithDataImport();
    }

    @Override
    public String getImportTypeId() {
        return IMPORT_TYPE_ID;
    }

    @Override
    public ImportPreviewResponse process(List<BrAPIImport> brAPIImports, Table data, Program program, ImportUpload upload, Boolean commit)
            throws UnprocessableEntityException {

        ImportPreviewResponse response = null;
        List<Processor> processors = List.of(germplasmProcessor,
                                             trialProcessor,
                                             studyProcessor,
                                             observationUnitProcessor);
        try {
            response = processorManager.process(brAPIImports, processors, program, upload, commit);
        } catch (ValidatorException e) {
            log.error(e.getMessage());
        }
        return response;

    }
}

