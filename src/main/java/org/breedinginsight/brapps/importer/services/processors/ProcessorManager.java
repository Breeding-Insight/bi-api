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
package org.breedinginsight.brapps.importer.services.processors;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.services.ImportStatusService;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.ValidatorException;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Prototype
@Slf4j
public class ProcessorManager {

    private List<Processor> processors;
    private final Map<Integer, PendingImport> mappedBrAPIImport;
    private final ImportStatusService statusService;
    private final Map<String, ImportPreviewStatistics> statistics = new HashMap<>();

    @Inject
    public ProcessorManager(ImportStatusService statusService) {
        this.processors = new ArrayList<>();
        this.mappedBrAPIImport = new HashMap<>();
        this.statusService = statusService;
    }

    public ImportPreviewResponse process(List<BrAPIImport> importRows, List<Processor> processors, Table data, Program program, ImportUpload upload, User user, boolean commit) throws Exception {

        this.processors = processors;

        // check existing brapi objects and map data for each registered type
        for (Processor processor : processors) {
            processor.initialize(importRows);
            log.debug("Checking existing " + processor.getName().toLowerCase() + " objects in brapi service and mapping data");
            statusService.updateMessage(upload, "Checking existing " + processor.getName().toLowerCase() + " objects in brapi service and mapping data");
            processor.getExistingBrapiData(importRows, program);
            Map<String, ImportPreviewStatistics> stats = processor.process(upload, importRows, mappedBrAPIImport, data, program, user, commit);
            statistics.putAll(stats);
        }

        ImportPreviewResponse response = new ImportPreviewResponse();
        response.setStatistics(statistics);
        List<PendingImport> mappedBrAPIImportList = new ArrayList<>(mappedBrAPIImport.values());
        response.setRows(mappedBrAPIImportList);
        response.setDynamicColumnNames(upload.getDynamicColumnNamesList());

        log.debug("Finished mapping data to brapi objects");
        statusService.updateMappedData(upload, response, "Finished mapping data to brapi objects");

        if (!commit) {
            statusService.updateOk(upload);
            return response;
        } else {
            validateProcessorDependencies();
            postBrapiData(program, upload);
        }

        return response;
    }

    private void validateProcessorDependencies() {
        for (Processor processor : processors) {
            try {
                processor.validateDependencies(mappedBrAPIImport);
            } catch (ValidatorException e) {
                // TODO: accumulate errors and return in response to UI
                log.error(e.getMessage());
            }
        }
    }

    private void postBrapiData(Program program, ImportUpload upload) throws ValidatorException {

        // get total number of new brapi objects to create
        long totalObjects = 0;
        for (ImportPreviewStatistics stats : statistics.values()) {
            totalObjects += stats.getNewObjectCount();
        }

        log.debug("Starting upload to brapi service");
        statusService.startUpload(upload, totalObjects, "Starting upload to brapi service");

        for (Processor processor : processors) {
            log.debug("Creating new " + processor.getName().toLowerCase() + " objects in brapi service");
            statusService.updateMessage(upload, "Creating new " + processor.getName().toLowerCase() + " objects in brapi service");
            processor.postBrapiData(mappedBrAPIImport, program, upload);
        }

        log.debug("Completed upload to brapi service");
        statusService.finishUpload(upload, totalObjects, "Completed upload to brapi service");
    }

}
